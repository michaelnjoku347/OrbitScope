//! Cubelight — a small ray tracer compiled to WebAssembly.
//!
//! Three cubes sit on a checker floor under a spherical lamp. The page can
//! switch the shading *system* (direct, Whitted, path, and a few diagnostic
//! views) and the print *format* (gamma, film curves, false color) without
//! retracing, because formats are applied to an accumulated HDR buffer.
//!
//! Parameter layout is shared with `web/app.js`. Keep the indices in sync.

use std::cell::UnsafeCell;

// --- shared parameter block -------------------------------------------------

pub const PARAM_LEN: usize = 67;

pub const WIDTH: usize = 0;
pub const HEIGHT: usize = 1;
pub const SYSTEM: usize = 2;
pub const FORMAT: usize = 3;
pub const BOUNCES: usize = 4;
pub const SKY: usize = 5;
pub const SEED: usize = 6;
pub const YAW: usize = 7;
pub const PITCH: usize = 8;
pub const DIST: usize = 9;
pub const FOV: usize = 10;
pub const EXPOSURE: usize = 11;
pub const VIGNETTE: usize = 12;
pub const LIGHT_ANGLE: usize = 13;
pub const LIGHT_ORBIT: usize = 14;
pub const LIGHT_HEIGHT: usize = 15;
pub const LIGHT_SIZE: usize = 16;
pub const LIGHT_POWER: usize = 17;
pub const LIGHT_R: usize = 18;
pub const LIGHT_G: usize = 19;
pub const LIGHT_B: usize = 20;
pub const FLOOR_R: usize = 21;
pub const FLOOR_G: usize = 22;
pub const FLOOR_B: usize = 23;
pub const FLOOR_ROUGH: usize = 24;
pub const FLOOR_METAL: usize = 25;
pub const CHECKER: usize = 26;
pub const CHECKER_SCALE: usize = 27;
pub const CUBE0: usize = 28;
pub const CUBE_STRIDE: usize = 13;

pub const C_X: usize = 0;
pub const C_Z: usize = 1;
pub const C_LIFT: usize = 2;
pub const C_SIZE: usize = 3;
pub const C_ROT: usize = 4;
pub const C_METAL: usize = 5;
pub const C_ROUGH: usize = 6;
pub const C_TRANS: usize = 7;
pub const C_EMIT: usize = 8;
pub const C_R: usize = 9;
pub const C_G: usize = 10;
pub const C_B: usize = 11;
pub const C_IOR: usize = 12;

const SYS_DIRECT: u32 = 0;
const SYS_WHITTED: u32 = 1;
const SYS_PATH: u32 = 2;
const SYS_NORMALS: u32 = 3;
const SYS_DEPTH: u32 = 4;
const SYS_AO: u32 = 5;
const SYS_ALBEDO: u32 = 6;

const ID_LIGHT: i32 = 4;
const ID_FLOOR: i32 = 8;

const AMBIENT: f32 = 0.24;
const SPEC_GAIN: f32 = 0.22;
const MAX_RADIANCE: f32 = 24.0;

// --- math -------------------------------------------------------------------

#[derive(Clone, Copy, Debug)]
struct Vec3 {
    x: f32,
    y: f32,
    z: f32,
}

impl Vec3 {
    const ZERO: Self = Self {
        x: 0.0,
        y: 0.0,
        z: 0.0,
    };
    const ONE: Self = Self {
        x: 1.0,
        y: 1.0,
        z: 1.0,
    };

    fn new(x: f32, y: f32, z: f32) -> Self {
        Self { x, y, z }
    }

    fn splat(v: f32) -> Self {
        Self { x: v, y: v, z: v }
    }

    fn dot(self, o: Self) -> f32 {
        self.x * o.x + self.y * o.y + self.z * o.z
    }

    fn cross(self, o: Self) -> Self {
        Self::new(
            self.y * o.z - self.z * o.y,
            self.z * o.x - self.x * o.z,
            self.x * o.y - self.y * o.x,
        )
    }

    fn length(self) -> f32 {
        self.dot(self).sqrt()
    }

    fn normalized(self) -> Self {
        let len = self.length();
        if len < 1e-8 {
            Self::ZERO
        } else {
            self / len
        }
    }

    fn max_comp(self) -> f32 {
        self.x.max(self.y).max(self.z)
    }

    fn clamp(self, lo: f32, hi: f32) -> Self {
        Self::new(
            self.x.clamp(lo, hi),
            self.y.clamp(lo, hi),
            self.z.clamp(lo, hi),
        )
    }

    fn lerp(self, o: Self, t: f32) -> Self {
        self * (1.0 - t) + o * t
    }

    fn finite(self) -> bool {
        self.x.is_finite() && self.y.is_finite() && self.z.is_finite()
    }
}

impl std::ops::Add for Vec3 {
    type Output = Self;
    fn add(self, o: Self) -> Self {
        Self::new(self.x + o.x, self.y + o.y, self.z + o.z)
    }
}
impl std::ops::Sub for Vec3 {
    type Output = Self;
    fn sub(self, o: Self) -> Self {
        Self::new(self.x - o.x, self.y - o.y, self.z - o.z)
    }
}
impl std::ops::Mul<f32> for Vec3 {
    type Output = Self;
    fn mul(self, s: f32) -> Self {
        Self::new(self.x * s, self.y * s, self.z * s)
    }
}
impl std::ops::Mul<Vec3> for Vec3 {
    type Output = Self;
    fn mul(self, o: Self) -> Self {
        Self::new(self.x * o.x, self.y * o.y, self.z * o.z)
    }
}
impl std::ops::Div<f32> for Vec3 {
    type Output = Self;
    fn div(self, s: f32) -> Self {
        Self::new(self.x / s, self.y / s, self.z / s)
    }
}
impl std::ops::Neg for Vec3 {
    type Output = Self;
    fn neg(self) -> Self {
        Self::new(-self.x, -self.y, -self.z)
    }
}

struct Rng(u32);

impl Rng {
    fn new(seed: u32) -> Self {
        let mut r = Self(seed.wrapping_mul(747796405).wrapping_add(2891336453) | 1);
        r.next();
        r
    }

    fn next(&mut self) -> u32 {
        let mut x = self.0;
        self.0 = x.wrapping_mul(747796405).wrapping_add(2891336453);
        x = ((x >> ((x >> 28) + 4)) ^ x).wrapping_mul(277803737);
        (x >> 22) ^ x
    }

    fn f32(&mut self) -> f32 {
        (self.next() >> 8) as f32 * (1.0 / 16_777_216.0)
    }
}

fn hash_pixel(x: u32, y: u32, sample: u32, seed: u32) -> u32 {
    let mut h = x.wrapping_mul(374761393)
        ^ y.wrapping_mul(668265263)
        ^ sample.wrapping_mul(1440662683)
        ^ seed.wrapping_mul(2246822519);
    h = (h ^ (h >> 13)).wrapping_mul(1274126177);
    h ^ (h >> 16)
}

fn srgb_byte_to_linear(u: u8) -> f32 {
    let x = u as f32 / 255.0;
    if x <= 0.04045 {
        x / 12.92
    } else {
        ((x + 0.055) / 1.055).powf(2.4)
    }
}

fn srgb_u8(r: u8, g: u8, b: u8) -> Vec3 {
    Vec3::new(
        srgb_byte_to_linear(r),
        srgb_byte_to_linear(g),
        srgb_byte_to_linear(b),
    )
}

fn linear_to_srgb(c: f32) -> f32 {
    let c = c.clamp(0.0, 1.0);
    if c <= 0.0031308 {
        12.92 * c
    } else {
        1.055 * c.powf(1.0 / 2.4) - 0.055
    }
}

// --- scene ------------------------------------------------------------------

#[derive(Clone, Copy)]
struct Ray {
    origin: Vec3,
    dir: Vec3,
}

#[derive(Clone, Copy)]
struct Hit {
    t: f32,
    p: Vec3,
    n: Vec3,
    albedo: Vec3,
    metallic: f32,
    roughness: f32,
    transmission: f32,
    emission: Vec3,
    ior: f32,
    thickness: f32,
    id: i32,
}

#[derive(Clone, Copy)]
struct Cube {
    center: Vec3,
    half: f32,
    rot: f32,
    albedo: Vec3,
    metallic: f32,
    roughness: f32,
    transmission: f32,
    emission: Vec3,
    ior: f32,
    id: i32,
}

#[derive(Clone, Copy)]
struct Lamp {
    pos: Vec3,
    color: Vec3,
    power: f32,
    radius: f32,
    skip: i32,
}

struct Scene {
    cubes: [Cube; 3],
    floor_albedo: Vec3,
    floor_metallic: f32,
    floor_roughness: f32,
    checker: bool,
    checker_scale: f32,
    light_pos: Vec3,
    light_radius: f32,
    light_color: Vec3,
    light_emission: Vec3,
    lamps: [Lamp; 4],
    lamp_count: usize,
    sky: u32,
    bounces: u32,
    system: u32,
    sun_dir: Vec3,
}

#[derive(Clone, Copy)]
struct Camera {
    eye: Vec3,
    lower_left: Vec3,
    horizontal: Vec3,
    vertical: Vec3,
}

fn rot_y(v: Vec3, a: f32) -> Vec3 {
    let (s, c) = a.sin_cos();
    Vec3::new(c * v.x + s * v.z, v.y, -s * v.x + c * v.z)
}

fn cube_field(p: &[f32], index: usize, field: usize) -> f32 {
    p[CUBE0 + index * CUBE_STRIDE + field]
}

fn build_scene(p: &[f32]) -> Scene {
    let mut cubes = [Cube {
        center: Vec3::ZERO,
        half: 0.5,
        rot: 0.0,
        albedo: Vec3::ONE,
        metallic: 0.0,
        roughness: 0.5,
        transmission: 0.0,
        emission: Vec3::ZERO,
        ior: 1.5,
        id: 0,
    }; 3];

    for i in 0..3 {
        let size = cube_field(p, i, C_SIZE).clamp(0.2, 3.2);
        let half = size * 0.5;
        let lift = cube_field(p, i, C_LIFT).clamp(-0.4, 3.0);
        let trans = cube_field(p, i, C_TRANS).clamp(0.0, 1.0);
        let metal = cube_field(p, i, C_METAL).clamp(0.0, 1.0) * (1.0 - trans);
        let emit = cube_field(p, i, C_EMIT).clamp(0.0, 16.0);
        let albedo = Vec3::new(
            cube_field(p, i, C_R).clamp(0.0, 4.0),
            cube_field(p, i, C_G).clamp(0.0, 4.0),
            cube_field(p, i, C_B).clamp(0.0, 4.0),
        );
        cubes[i] = Cube {
            center: Vec3::new(cube_field(p, i, C_X), half + lift, cube_field(p, i, C_Z)),
            half,
            rot: cube_field(p, i, C_ROT).to_radians(),
            albedo,
            metallic: metal,
            roughness: cube_field(p, i, C_ROUGH).clamp(0.0, 1.0),
            transmission: trans,
            emission: albedo * emit * 1.15,
            ior: cube_field(p, i, C_IOR).clamp(1.05, 2.6),
            id: i as i32,
        };
    }

    let angle = p[LIGHT_ANGLE].to_radians();
    let orbit = p[LIGHT_ORBIT].clamp(0.4, 14.0);
    let light_pos = Vec3::new(
        orbit * angle.sin(),
        p[LIGHT_HEIGHT].clamp(0.3, 16.0),
        orbit * angle.cos(),
    );
    let light_color = Vec3::new(
        p[LIGHT_R].clamp(0.0, 4.0),
        p[LIGHT_G].clamp(0.0, 4.0),
        p[LIGHT_B].clamp(0.0, 4.0),
    );
    let light_power = p[LIGHT_POWER].clamp(0.0, 200.0);
    let light_radius = p[LIGHT_SIZE].clamp(0.05, 2.5);

    let mut lamps = [Lamp {
        pos: Vec3::ZERO,
        color: Vec3::ZERO,
        power: 0.0,
        radius: 0.0,
        skip: -1,
    }; 4];
    lamps[0] = Lamp {
        pos: light_pos,
        color: light_color,
        power: light_power,
        radius: light_radius,
        skip: -1,
    };
    let mut lamp_count = 1;
    for cube in &cubes {
        if cube.emission.max_comp() > 0.15 && cube.transmission < 0.5 && lamp_count < 4 {
            lamps[lamp_count] = Lamp {
                pos: cube.center,
                color: cube.albedo,
                power: cube_field(p, cube.id as usize, C_EMIT).clamp(0.0, 16.0) * 5.5,
                radius: cube.half * 0.45,
                skip: cube.id,
            };
            lamp_count += 1;
        }
    }

    Scene {
        cubes,
        floor_albedo: Vec3::new(p[FLOOR_R], p[FLOOR_G], p[FLOOR_B]).clamp(0.0, 4.0),
        floor_metallic: p[FLOOR_METAL].clamp(0.0, 1.0),
        floor_roughness: p[FLOOR_ROUGH].clamp(0.0, 1.0),
        checker: p[CHECKER] > 0.5,
        checker_scale: p[CHECKER_SCALE].clamp(0.05, 4.0),
        light_pos,
        light_radius,
        light_color,
        light_emission: light_color * (6.0 + light_power * 0.12),
        lamps,
        lamp_count,
        sky: p[SKY] as u32,
        bounces: (p[BOUNCES] as u32).clamp(1, 6),
        system: (p[SYSTEM] as u32).min(SYS_ALBEDO),
        sun_dir: light_pos.normalized(),
    }
}

fn build_camera(p: &[f32], width: u32, height: u32) -> Camera {
    let target = Vec3::new(0.0, 0.62, 0.0);
    let pitch = p[PITCH].clamp(0.12, 1.35);
    let dist = p[DIST].clamp(2.4, 22.0);
    let yaw = p[YAW];
    let eye = target
        + Vec3::new(
            dist * pitch.cos() * yaw.sin(),
            dist * pitch.sin(),
            dist * pitch.cos() * yaw.cos(),
        );
    let forward = (target - eye).normalized();
    let right = forward.cross(Vec3::new(0.0, 1.0, 0.0)).normalized();
    let up = right.cross(forward).normalized();
    let aspect = width as f32 / height.max(1) as f32;
    let half = (p[FOV].clamp(15.0, 90.0).to_radians() * 0.5).tan();
    let horizontal = right * (2.0 * half * aspect);
    let vertical = up * (2.0 * half);
    let lower_left = eye + forward - horizontal * 0.5 - vertical * 0.5;
    Camera {
        eye,
        lower_left,
        horizontal,
        vertical,
    }
}

fn intersect_box(ro: Vec3, rd: Vec3, half: f32) -> Option<(f32, Vec3)> {
    let origin = [ro.x, ro.y, ro.z];
    let dir = [rd.x, rd.y, rd.z];
    let mut tmin = -1.0e30f32;
    let mut tmax = 1.0e30f32;
    let mut n_enter = [0.0f32; 3];
    let mut n_exit = [0.0f32; 3];

    for axis in 0..3 {
        let d = dir[axis];
        let o = origin[axis];
        if d.abs() < 1e-8 {
            if o < -half || o > half {
                return None;
            }
            continue;
        }
        let inv = 1.0 / d;
        let mut t0 = (-half - o) * inv;
        let mut t1 = (half - o) * inv;
        let mut s0 = -1.0f32;
        let mut s1 = 1.0f32;
        if t0 > t1 {
            std::mem::swap(&mut t0, &mut t1);
            std::mem::swap(&mut s0, &mut s1);
        }
        if t0 > tmin {
            tmin = t0;
            n_enter = [0.0, 0.0, 0.0];
            n_enter[axis] = s0;
        }
        if t1 < tmax {
            tmax = t1;
            n_exit = [0.0, 0.0, 0.0];
            n_exit[axis] = s1;
        }
        if tmax < tmin {
            return None;
        }
    }

    if tmax < 1e-3 {
        return None;
    }
    if tmin > 1e-3 {
        Some((tmin, Vec3::new(n_enter[0], n_enter[1], n_enter[2])))
    } else {
        Some((tmax, Vec3::new(n_exit[0], n_exit[1], n_exit[2])))
    }
}

fn intersect_sphere(ro: Vec3, rd: Vec3, center: Vec3, radius: f32) -> Option<f32> {
    let oc = ro - center;
    let b = oc.dot(rd);
    let c = oc.dot(oc) - radius * radius;
    let disc = b * b - c;
    if disc < 0.0 {
        return None;
    }
    let s = disc.sqrt();
    let t0 = -b - s;
    if t0 > 1e-3 {
        return Some(t0);
    }
    let t1 = -b + s;
    if t1 > 1e-3 {
        Some(t1)
    } else {
        None
    }
}

fn floor_albedo_at(scene: &Scene, p: Vec3) -> Vec3 {
    if !scene.checker {
        return scene.floor_albedo;
    }
    let s = scene.checker_scale;
    let ix = (p.x * s).floor() as i32;
    let iz = (p.z * s).floor() as i32;
    if (ix + iz) & 1 == 0 {
        scene.floor_albedo * 0.42
    } else {
        scene.floor_albedo
    }
}

fn consider(best: &mut Option<Hit>, hit: Hit) {
    if hit.t > 1e-3 && best.map(|b| hit.t < b.t).unwrap_or(true) {
        *best = Some(hit);
    }
}

fn hit_scene(scene: &Scene, ray: Ray, include_light: bool, skip: &[i32]) -> Option<Hit> {
    let mut best: Option<Hit> = None;

    if ray.dir.y < -1e-6 {
        let t = -ray.origin.y / ray.dir.y;
        if t > 1e-3 && !skip.contains(&ID_FLOOR) {
            let p = ray.origin + ray.dir * t;
            consider(
                &mut best,
                Hit {
                    t,
                    p,
                    n: Vec3::new(0.0, 1.0, 0.0),
                    albedo: floor_albedo_at(scene, p),
                    metallic: scene.floor_metallic,
                    roughness: scene.floor_roughness,
                    transmission: 0.0,
                    emission: Vec3::ZERO,
                    ior: 1.5,
                    thickness: 0.0,
                    id: ID_FLOOR,
                },
            );
        }
    }

    for cube in &scene.cubes {
        if skip.contains(&cube.id) {
            continue;
        }
        let local_o = rot_y(ray.origin - cube.center, -cube.rot);
        let local_d = rot_y(ray.dir, -cube.rot);
        if let Some((t, n_local)) = intersect_box(local_o, local_d, cube.half) {
            let n = rot_y(n_local, cube.rot).normalized();
            consider(
                &mut best,
                Hit {
                    t,
                    p: ray.origin + ray.dir * t,
                    n,
                    albedo: cube.albedo,
                    metallic: cube.metallic,
                    roughness: cube.roughness,
                    transmission: cube.transmission,
                    emission: cube.emission,
                    ior: cube.ior,
                    thickness: cube.half * 2.0,
                    id: cube.id,
                },
            );
        }
    }

    if include_light {
        if let Some(t) = intersect_sphere(ray.origin, ray.dir, scene.light_pos, scene.light_radius)
        {
            let p = ray.origin + ray.dir * t;
            let n = (p - scene.light_pos).normalized();
            consider(
                &mut best,
                Hit {
                    t,
                    p,
                    n,
                    albedo: scene.light_color,
                    metallic: 0.0,
                    roughness: 1.0,
                    transmission: 0.0,
                    emission: scene.light_emission,
                    ior: 1.0,
                    thickness: scene.light_radius * 2.0,
                    id: ID_LIGHT,
                },
            );
        }
    }

    best
}

fn sky_gradient(dir: Vec3, sky: u32) -> Vec3 {
    let up = dir.y.clamp(0.0, 1.0);
    let horizon_mix = up.powf(0.65);
    match sky {
        1 => Vec3::new(0.95, 0.38, 0.18).lerp(Vec3::new(0.07, 0.08, 0.18), horizon_mix),
        2 => {
            let night =
                Vec3::new(0.025, 0.03, 0.05).lerp(Vec3::new(0.004, 0.005, 0.012), horizon_mix);
            let cell = Vec3::new(
                (dir.x * 90.0).floor(),
                (dir.y * 90.0).floor(),
                (dir.z * 90.0).floor(),
            );
            let hv = cell.dot(Vec3::new(127.1, 311.7, 74.7)).sin() * 43758.5453;
            let hv = hv - hv.floor();
            let star = if hv > 0.992 && dir.y > 0.0 {
                (hv - 0.992) * 40.0
            } else {
                0.0
            };
            night + Vec3::splat(star)
        }
        3 => Vec3::ZERO,
        _ => Vec3::new(0.78, 0.84, 0.93).lerp(Vec3::new(0.28, 0.48, 0.86), horizon_mix),
    }
}

fn sky_color(dir: Vec3, scene: &Scene) -> Vec3 {
    let base = sky_gradient(dir, scene.sky);
    if scene.sky == 3 {
        return base;
    }
    let d = dir.dot(scene.sun_dir).max(0.0);
    base + scene.light_color * (d.powf(14.0) * 0.18 + d.powf(220.0) * 2.4)
}

fn reflect(i: Vec3, n: Vec3) -> Vec3 {
    i - n * (2.0 * i.dot(n))
}

fn refract(i: Vec3, n: Vec3, eta: f32) -> Option<Vec3> {
    let cosi = (-i.dot(n)).clamp(-1.0, 1.0);
    let k = 1.0 - eta * eta * (1.0 - cosi * cosi);
    if k < 0.0 {
        None
    } else {
        Some((i * eta + n * (eta * cosi - k.sqrt())).normalized())
    }
}

fn fresnel_dielectric(cos_i: f32, eta_i: f32, eta_t: f32) -> f32 {
    let cos_i = cos_i.clamp(0.0, 1.0);
    let sin_t2 = (eta_i / eta_t).powi(2) * (1.0 - cos_i * cos_i);
    if sin_t2 >= 1.0 {
        return 1.0;
    }
    let cos_t = (1.0 - sin_t2).sqrt();
    let rs = (eta_i * cos_i - eta_t * cos_t) / (eta_i * cos_i + eta_t * cos_t);
    let rp = (eta_i * cos_t - eta_t * cos_i) / (eta_i * cos_t + eta_t * cos_i);
    ((rs * rs + rp * rp) * 0.5).clamp(0.0, 1.0)
}

fn fresnel_conductor(cos: f32, albedo: Vec3) -> Vec3 {
    let m = (1.0 - cos.clamp(0.0, 1.0)).powi(5);
    albedo + (Vec3::ONE - albedo) * m
}

fn beer(color: Vec3, dist: f32) -> Vec3 {
    let d = dist.max(0.0);
    Vec3::new(
        color.x.clamp(0.02, 1.0).powf(d),
        color.y.clamp(0.02, 1.0).powf(d),
        color.z.clamp(0.02, 1.0).powf(d),
    )
}

fn random_unit(rng: &mut Rng) -> Vec3 {
    let z = rng.f32() * 2.0 - 1.0;
    let a = rng.f32() * std::f32::consts::TAU;
    let r = (1.0 - z * z).max(0.0).sqrt();
    Vec3::new(r * a.cos(), z, r * a.sin())
}

fn basis(n: Vec3) -> (Vec3, Vec3) {
    let up = if n.y.abs() < 0.999 {
        Vec3::new(0.0, 1.0, 0.0)
    } else {
        Vec3::new(1.0, 0.0, 0.0)
    };
    let t = up.cross(n).normalized();
    let b = n.cross(t);
    (t, b)
}

fn cosine_hemisphere(n: Vec3, rng: &mut Rng) -> Vec3 {
    let r1 = rng.f32();
    let phi = rng.f32() * std::f32::consts::TAU;
    let r = r1.sqrt();
    let x = r * phi.cos();
    let z = r * phi.sin();
    let y = (1.0 - r1).max(0.0).sqrt();
    let (t, b) = basis(n);
    (t * x + n * y + b * z).normalized()
}

fn sample_sphere(center: Vec3, radius: f32, rng: &mut Rng) -> Vec3 {
    if radius <= 1e-4 {
        return center;
    }
    center + random_unit(rng) * radius
}

/// How much light gets through along a shadow ray. Glass tints; opaque blocks.
fn transmittance(scene: &Scene, origin: Vec3, dir: Vec3, max_t: f32, skip_id: i32) -> Vec3 {
    let mut origin = origin;
    let mut remain = max_t;
    let mut passed = [skip_id, i32::MIN, i32::MIN, i32::MIN];
    let mut passed_n = 1usize;
    let mut color = Vec3::ONE;

    for _ in 0..5 {
        if remain < 1e-3 {
            break;
        }
        let Some(hit) = hit_scene(scene, Ray { origin, dir }, false, &passed[..passed_n]) else {
            break;
        };
        if hit.t >= remain {
            break;
        }
        if hit.transmission > 0.5 {
            color = color * beer(hit.albedo, (hit.thickness * 0.55).max(0.25));
            if passed_n < passed.len() {
                passed[passed_n] = hit.id;
                passed_n += 1;
            }
            let step = hit.t + 0.03;
            origin = origin + dir * step;
            remain -= step;
            continue;
        }
        return Vec3::ZERO;
    }
    color
}

fn face_normal(hit: &Hit, ray_dir: Vec3) -> Vec3 {
    if hit.n.dot(ray_dir) > 0.0 {
        -hit.n
    } else {
        hit.n
    }
}

fn specular_lobe(n: Vec3, view: Vec3, light_dir: Vec3, rough: f32) -> f32 {
    let h = (view + light_dir).normalized();
    if h.length() < 0.5 {
        return 0.0;
    }
    let ndoth = n.dot(h).max(0.0);
    let shininess = (2.0 / (rough * rough + 0.02)).clamp(10.0, 256.0);
    let norm = (shininess + 2.0) / (2.0 * std::f32::consts::PI);
    ndoth.powf(shininess) * norm
}

struct LightSample {
    diffuse: Vec3,
    specular: Vec3,
}

fn sample_lamps(
    scene: &Scene,
    hit: &Hit,
    n: Vec3,
    view: Vec3,
    rng: &mut Rng,
    soft: bool,
    analytic_spec: bool,
) -> LightSample {
    let mut diffuse = Vec3::ZERO;
    let mut specular = Vec3::ZERO;
    let mirror = !analytic_spec && (hit.metallic > 0.45 || hit.roughness < 0.18);
    for lamp in scene.lamps.iter().take(scene.lamp_count) {
        if lamp.skip == hit.id {
            continue;
        }
        let point = if soft {
            sample_sphere(lamp.pos, lamp.radius, rng)
        } else {
            lamp.pos
        };
        let to = point - hit.p;
        let dist = to.length().max(1e-3);
        let dir = to / dist;
        let ndotl = n.dot(dir);
        if ndotl <= 0.0 {
            continue;
        }
        let vis = transmittance(scene, hit.p + n * 0.02, dir, dist - 0.04, hit.id);
        if vis.max_comp() <= 0.0 {
            continue;
        }
        let incoming = lamp.color * (lamp.power / (dist * dist)) * vis;
        diffuse = diffuse
            + hit.albedo * (1.0 - hit.metallic) * (1.0 - hit.transmission) * incoming * ndotl;
        if !mirror {
            let lobe = specular_lobe(n, view, dir, hit.roughness.max(0.12));
            let spec_col = Vec3::ONE.lerp(hit.albedo, hit.metallic);
            specular = specular + spec_col * incoming * lobe * SPEC_GAIN;
        }
    }
    LightSample { diffuse, specular }
}

fn ambient(scene: &Scene, hit: &Hit, n: Vec3) -> Vec3 {
    let sky = sky_gradient(n, scene.sky);
    let wrap = 0.4 + 0.6 * n.y.clamp(0.0, 1.0);
    hit.albedo * (1.0 - hit.metallic) * (1.0 - hit.transmission) * sky * AMBIENT * wrap
}

fn shade(scene: &Scene, ray: Ray, depth: u32, rng: &mut Rng, soft: bool) -> Vec3 {
    if ray.dir.length() < 1e-6 {
        return Vec3::ZERO;
    }
    if depth >= scene.bounces {
        return sky_color(ray.dir, scene);
    }
    let Some(hit) = hit_scene(scene, ray, true, &[]) else {
        return match scene.system {
            SYS_NORMALS => ray.dir * 0.5 + Vec3::splat(0.5),
            SYS_DEPTH => Vec3::new(0.03, 0.04, 0.07),
            SYS_AO => Vec3::ONE,
            SYS_ALBEDO => Vec3::new(0.08, 0.08, 0.09),
            _ => sky_color(ray.dir, scene),
        };
    };

    if scene.system == SYS_NORMALS && depth == 0 {
        let n = face_normal(&hit, ray.dir);
        return n * 0.5 + Vec3::splat(0.5);
    }
    if scene.system == SYS_DEPTH && depth == 0 {
        let t = (hit.t / 16.0).clamp(0.0, 1.0);
        return Vec3::new(0.96, 0.91, 0.82).lerp(Vec3::new(0.05, 0.07, 0.14), t);
    }
    if scene.system == SYS_ALBEDO && depth == 0 {
        return if hit.id == ID_LIGHT {
            Vec3::ONE
        } else {
            hit.albedo
        };
    }
    if scene.system == SYS_AO && depth == 0 {
        return Vec3::splat(occlusion(scene, &hit, rng, soft));
    }
    if hit.id == ID_LIGHT {
        return hit.emission;
    }
    if hit.transmission > 0.5 && scene.system <= SYS_PATH {
        return shade_glass(scene, ray, &hit, depth, rng, soft);
    }

    match scene.system {
        SYS_PATH => shade_path(scene, ray, &hit, rng, soft),
        SYS_WHITTED => shade_whitted(scene, ray, &hit, depth, rng, soft),
        SYS_DIRECT => shade_direct(scene, ray, &hit, rng, soft),
        _ => shade_direct(scene, ray, &hit, rng, soft),
    }
}

fn occlusion(scene: &Scene, hit: &Hit, _rng: &mut Rng, _soft: bool) -> f32 {
    let n = hit.n;
    let samples = 8;
    let jitter = (hit.p.x * 17.13).sin() * 43758.5453 + (hit.p.z * 13.37).sin() * 12345.678;
    let spin = (jitter - jitter.floor()) * std::f32::consts::TAU;
    let mut vis = 0.0f32;
    let (tan, bit) = basis(n);
    for i in 0..samples {
        let t = (i as f32 + 0.5) / samples as f32;
        let phi = i as f32 * 2.3999632 + spin;
        let r = t.sqrt();
        let y = (1.0 - t).max(0.0).sqrt();
        let dir = (tan * (r * phi.cos()) + n * y + bit * (r * phi.sin())).normalized();
        let blocked = hit_scene(
            scene,
            Ray {
                origin: hit.p + n * 0.03,
                dir,
            },
            false,
            &[hit.id],
        )
        .map(|h| h.t < 1.7 && h.transmission < 0.5)
        .unwrap_or(false);
        if !blocked {
            vis += 1.0;
        }
    }
    vis / samples as f32
}

fn shade_direct(scene: &Scene, ray: Ray, hit: &Hit, rng: &mut Rng, soft: bool) -> Vec3 {
    let n = face_normal(hit, ray.dir);
    let view = -ray.dir;
    let lit = sample_lamps(scene, hit, n, view, rng, soft, true);
    let mut color = hit.emission + lit.diffuse + lit.specular + ambient(scene, hit, n);
    if hit.metallic > 0.15 && hit.transmission < 0.5 {
        let refl = reflect(ray.dir, n).normalized();
        if refl.length() > 0.5 {
            let env = sky_gradient(refl, scene.sky);
            color =
                color + fresnel_conductor(n.dot(view).max(0.0), hit.albedo) * env * hit.metallic;
        }
    }
    color
}

fn mirror_dir(hit: &Hit, ray_dir: Vec3, n: Vec3, rng: &mut Rng, soft: bool) -> Vec3 {
    let mut dir = reflect(ray_dir, n);
    if soft && hit.roughness > 0.02 {
        dir = (dir + random_unit(rng) * hit.roughness).normalized();
        if dir.dot(n) < 0.0 {
            dir = reflect(ray_dir, n);
        }
    }
    dir
}

fn shade_whitted(
    scene: &Scene,
    ray: Ray,
    hit: &Hit,
    depth: u32,
    rng: &mut Rng,
    soft: bool,
) -> Vec3 {
    if hit.transmission > 0.5 {
        return shade_glass(scene, ray, hit, depth, rng, soft);
    }
    let n = face_normal(hit, ray.dir);
    let view = -ray.dir;
    let lit = sample_lamps(scene, hit, n, view, rng, soft, false);
    let mut color = hit.emission + lit.diffuse + lit.specular + ambient(scene, hit, n);

    let reflective = hit.metallic > 0.45 || hit.roughness < 0.18;
    if reflective && depth + 1 < scene.bounces {
        let blur = soft && hit.roughness > 0.35;
        let dir = mirror_dir(hit, ray.dir, n, rng, blur);
        let rc = shade(
            scene,
            Ray {
                origin: hit.p + n * 0.02,
                dir,
            },
            depth + 1,
            rng,
            soft,
        );
        let spec = if hit.metallic > 0.45 {
            fresnel_conductor(n.dot(view).clamp(0.0, 1.0), hit.albedo)
        } else {
            let f = fresnel_dielectric(n.dot(view).clamp(0.0, 1.0), 1.0, 1.5);
            Vec3::splat(f * (1.0 - hit.roughness).clamp(0.0, 1.0))
        };
        color = color + rc * spec;
    }
    color
}

fn shade_glass(scene: &Scene, ray: Ray, hit: &Hit, depth: u32, rng: &mut Rng, soft: bool) -> Vec3 {
    let outward = hit.n;
    let front = ray.dir.dot(outward) < 0.0;
    let n = if front { outward } else { -outward };
    let eta_i = if front { 1.0 } else { hit.ior };
    let eta_t = if front { hit.ior } else { 1.0 };
    let cos_i = (-ray.dir.dot(n)).clamp(0.0, 1.0);
    let f = fresnel_dielectric(cos_i, eta_i, eta_t);
    let reflect_dir = if soft && hit.roughness > 0.01 {
        mirror_dir(hit, ray.dir, n, rng, soft)
    } else {
        reflect(ray.dir, n)
    };
    let reflected = shade(
        scene,
        Ray {
            origin: hit.p + n * 0.015,
            dir: reflect_dir,
        },
        depth + 1,
        rng,
        soft,
    );
    if let Some(tdir) = refract(ray.dir, n, eta_i / eta_t) {
        let transmitted = shade(
            scene,
            Ray {
                origin: hit.p - n * 0.015,
                dir: tdir,
            },
            depth + 1,
            rng,
            soft,
        );
        let tint = if front {
            beer(hit.albedo, (hit.thickness * 0.5).max(0.3))
        } else {
            Vec3::ONE
        };
        hit.emission + reflected * f + transmitted * (1.0 - f) * tint
    } else {
        hit.emission + reflected
    }
}

fn shade_path(scene: &Scene, ray: Ray, first: &Hit, rng: &mut Rng, soft: bool) -> Vec3 {
    let mut ray = ray;
    let mut hit = *first;
    let mut throughput = Vec3::ONE;
    let mut radiance = Vec3::ZERO;
    let mut count_emission = true;

    for bounce in 0..scene.bounces {
        if hit.transmission > 0.5 {
            let glass = shade_glass(scene, ray, &hit, bounce, rng, soft);
            let total = radiance + throughput * glass;
            return if total.finite() {
                total.clamp(0.0, MAX_RADIANCE)
            } else {
                Vec3::ZERO
            };
        }
        if count_emission {
            radiance = radiance + throughput * hit.emission;
        }
        if hit.id == ID_LIGHT {
            break;
        }

        let n = face_normal(&hit, ray.dir);
        let view = -ray.dir;
        let trans = hit.transmission;
        let metal = hit.metallic;
        let diffuse_bounce = trans < 0.5 && metal < 0.5;

        if diffuse_bounce {
            let lit = sample_lamps(scene, &hit, n, view, rng, soft || bounce > 0, false);
            radiance = radiance + throughput * (lit.diffuse + lit.specular);
            count_emission = false;
        } else {
            count_emission = true;
        }

        if bounce + 1 >= scene.bounces {
            break;
        }

        if metal > 0.5 {
            let dir = mirror_dir(&hit, ray.dir, n, rng, true);
            if dir.dot(n) <= 0.0 {
                break;
            }
            throughput = throughput * fresnel_conductor(n.dot(view).max(0.0), hit.albedo);
            ray = Ray {
                origin: hit.p + n * 0.02,
                dir,
            };
        } else {
            let dir = cosine_hemisphere(n, rng);
            throughput = throughput * hit.albedo;
            ray = Ray {
                origin: hit.p + n * 0.02,
                dir,
            };
        }

        if bounce >= 2 {
            let p = throughput.max_comp().min(0.92);
            if rng.f32() > p {
                break;
            }
            throughput = throughput / p.max(0.05);
        }
        if throughput.max_comp() > 6.0 {
            throughput = throughput * (6.0 / throughput.max_comp());
        }

        match hit_scene(scene, ray, true, &[]) {
            Some(next) => hit = next,
            None => {
                radiance = radiance + throughput * sky_color(ray.dir, scene);
                break;
            }
        }
    }

    if radiance.finite() {
        radiance.clamp(0.0, MAX_RADIANCE)
    } else {
        Vec3::ZERO
    }
}

// --- tonemap ----------------------------------------------------------------

fn aces(x: f32) -> f32 {
    let a = 2.51;
    let b = 0.03;
    let c = 2.43;
    let d = 0.59;
    let e = 0.14;
    ((x * (a * x + b)) / (x * (c * x + d) + e)).clamp(0.0, 1.0)
}

fn heatmap(t: f32) -> [u8; 3] {
    const STOPS: [[u8; 3]; 6] = [
        [18, 12, 36],
        [86, 36, 160],
        [36, 122, 214],
        [28, 186, 156],
        [242, 204, 48],
        [255, 250, 244],
    ];
    let t = t.clamp(0.0, 1.0) * (STOPS.len() - 1) as f32;
    let i = (t.floor() as usize).min(STOPS.len() - 2);
    let f = t - i as f32;
    let a = STOPS[i];
    let b = STOPS[i + 1];
    [
        (a[0] as f32 + (b[0] as f32 - a[0] as f32) * f) as u8,
        (a[1] as f32 + (b[1] as f32 - a[1] as f32) * f) as u8,
        (a[2] as f32 + (b[2] as f32 - a[2] as f32) * f) as u8,
    ]
}

fn tonemap(s: &mut State) {
    let w = s.width;
    let h = s.height;
    let n = (w * h) as usize;
    if s.spp == 0 || n == 0 {
        s.output[..n * 4].fill(0);
        return;
    }
    let format = s.params[FORMAT] as u32;
    let system = s.params[SYSTEM] as u32;
    let exposure = if system >= SYS_NORMALS {
        1.0
    } else {
        s.params[EXPOSURE].clamp(0.05, 8.0)
    };
    let vignette = s.params[VIGNETTE].clamp(0.0, 1.0);
    let inv = 1.0 / s.spp as f32;
    let aspect = w as f32 / h.max(1) as f32;

    for y in 0..h {
        for x in 0..w {
            let i = (y * w + x) as usize;
            let mut color = Vec3::new(
                s.accum[i * 3] * inv,
                s.accum[i * 3 + 1] * inv,
                s.accum[i * 3 + 2] * inv,
            );
            if !color.finite() {
                color = Vec3::ZERO;
            }
            color = color * exposure;
            if vignette > 0.0 {
                let u = (x as f32 + 0.5) / w as f32 - 0.5;
                let v = (y as f32 + 0.5) / h as f32 - 0.5;
                let d = (u * aspect) * (u * aspect) + v * v;
                let vig = (1.0 - d * 0.85 * vignette).clamp(0.35, 1.0);
                color = color * vig;
            }

            let rgb = if format == 5 {
                let y = 0.2126 * color.x + 0.7152 * color.y + 0.0722 * color.z;
                heatmap(y / (1.0 + y.max(0.0)))
            } else {
                let mapped = match format {
                    1 => Vec3::new(
                        color.x.max(0.0) / (1.0 + color.x.max(0.0)),
                        color.y.max(0.0) / (1.0 + color.y.max(0.0)),
                        color.z.max(0.0) / (1.0 + color.z.max(0.0)),
                    ),
                    2 => Vec3::new(
                        aces(color.x.max(0.0)),
                        aces(color.y.max(0.0)),
                        aces(color.z.max(0.0)),
                    ),
                    3 => {
                        let punch = |c: f32| {
                            let y = aces((c.max(0.0) * 1.22 - 0.02).max(0.0));
                            ((y - 0.5) * 1.16 + 0.5).clamp(0.0, 1.0)
                        };
                        Vec3::new(punch(color.x), punch(color.y), punch(color.z))
                    }
                    4 => color.clamp(0.0, 1.0),
                    _ => color.clamp(0.0, 1.0),
                };
                if format == 4 {
                    [
                        (mapped.x * 255.0) as u8,
                        (mapped.y * 255.0) as u8,
                        (mapped.z * 255.0) as u8,
                    ]
                } else {
                    [
                        (linear_to_srgb(mapped.x) * 255.0 + 0.5) as u8,
                        (linear_to_srgb(mapped.y) * 255.0 + 0.5) as u8,
                        (linear_to_srgb(mapped.z) * 255.0 + 0.5) as u8,
                    ]
                }
            };
            let o = i * 4;
            s.output[o] = rgb[0];
            s.output[o + 1] = rgb[1];
            s.output[o + 2] = rgb[2];
            s.output[o + 3] = 255;
        }
    }
}

// --- state ------------------------------------------------------------------

struct State {
    params: [f32; PARAM_LEN],
    accum: Vec<f32>,
    output: Vec<u8>,
    spp: u32,
    key: u64,
    width: u32,
    height: u32,
    max_pixels: usize,
}

struct Shared(UnsafeCell<State>);

// The page and the preview tool drive this from one thread. `Sync` lets the
// static exist; overlapping calls from two threads would be a data race.
unsafe impl Sync for Shared {}

static STATE: Shared = Shared(UnsafeCell::new(State {
    params: [0.0; PARAM_LEN],
    accum: Vec::new(),
    output: Vec::new(),
    spp: 0,
    key: 0,
    width: 0,
    height: 0,
    max_pixels: 0,
}));

fn state() -> &'static mut State {
    unsafe { &mut *STATE.0.get() }
}

fn scene_key(p: &[f32]) -> u64 {
    let mut h = 0xcbf29ce484222325u64;
    for (i, v) in p.iter().enumerate() {
        if i == FORMAT || i == EXPOSURE || i == VIGNETTE {
            continue;
        }
        h ^= v.to_bits() as u64;
        h = h.wrapping_mul(0x100000001b3);
    }
    h
}

fn set_defaults(p: &mut [f32]) {
    p.fill(0.0);
    p[WIDTH] = 640.0;
    p[HEIGHT] = 360.0;
    p[SYSTEM] = SYS_WHITTED as f32;
    p[FORMAT] = 2.0;
    p[BOUNCES] = 3.0;
    p[SKY] = 0.0;
    p[SEED] = 1.0;
    p[YAW] = -0.66;
    p[PITCH] = 0.42;
    p[DIST] = 7.6;
    p[FOV] = 36.0;
    p[EXPOSURE] = 1.08;
    p[VIGNETTE] = 0.28;
    p[LIGHT_ANGLE] = 52.0;
    p[LIGHT_ORBIT] = 3.3;
    p[LIGHT_HEIGHT] = 4.7;
    p[LIGHT_SIZE] = 0.34;
    p[LIGHT_POWER] = 40.0;
    let light = srgb_u8(255, 236, 214);
    p[LIGHT_R] = light.x;
    p[LIGHT_G] = light.y;
    p[LIGHT_B] = light.z;
    let floor = srgb_u8(176, 164, 148);
    p[FLOOR_R] = floor.x;
    p[FLOOR_G] = floor.y;
    p[FLOOR_B] = floor.z;
    p[FLOOR_ROUGH] = 0.62;
    p[FLOOR_METAL] = 0.0;
    p[CHECKER] = 1.0;
    p[CHECKER_SCALE] = 0.62;

    let cubes = [
        // x, z, lift, size, rot deg, metal, rough, trans, emit, r, g, b, ior
        [
            -2.05, 0.28, 0.0, 1.12, 16.0, 0.0, 0.72, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5,
        ],
        [
            0.02, -0.48, 0.0, 1.52, -20.0, 1.0, 0.05, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5,
        ],
        [
            1.72, 0.58, 0.0, 0.78, 12.0, 0.0, 0.02, 1.0, 0.0, 0.0, 0.0, 0.0, 1.45,
        ],
    ];
    let colors = [
        srgb_u8(226, 78, 52),
        srgb_u8(236, 239, 244),
        srgb_u8(112, 214, 196),
    ];
    for (i, cube) in cubes.iter().enumerate() {
        let b = CUBE0 + i * CUBE_STRIDE;
        for (k, v) in cube.iter().enumerate() {
            p[b + k] = *v;
        }
        p[b + C_R] = colors[i].x;
        p[b + C_G] = colors[i].y;
        p[b + C_B] = colors[i].z;
    }
}

fn prepare_frame(s: &mut State) {
    let mut w = (s.params[WIDTH] as u32).clamp(16, 960);
    let mut h = (s.params[HEIGHT] as u32).clamp(16, 540);
    while s.max_pixels > 0 && (w as usize) * (h as usize) > s.max_pixels {
        w = (w * 3 / 4).max(16);
        h = (h * 3 / 4).max(16);
    }
    let key = scene_key(&s.params);
    if key != s.key || w != s.width || h != s.height {
        let n = (w as usize) * (h as usize);
        s.accum[..n * 3].fill(0.0);
        s.spp = 0;
        s.width = w;
        s.height = h;
        s.key = key;
    }
}

fn render_sample(scene: &Scene, cam: Camera, s: &mut State, sample_index: u32, soft: bool) {
    let w = s.width;
    let h = s.height;
    let seed = s.params[SEED] as u32;
    for y in 0..h {
        for x in 0..w {
            let mut rng = Rng::new(hash_pixel(x, y, sample_index, seed));
            let (jx, jy) = if soft {
                (rng.f32() - 0.5, rng.f32() - 0.5)
            } else {
                (0.0, 0.0)
            };
            let u = (x as f32 + 0.5 + jx) / w as f32;
            let v = 1.0 - ((y as f32 + 0.5 + jy) / h as f32);
            let dir =
                (cam.lower_left + cam.horizontal * u + cam.vertical * v - cam.eye).normalized();
            if dir.length() < 1e-6 {
                continue;
            }
            let color = shade(
                scene,
                Ray {
                    origin: cam.eye,
                    dir,
                },
                0,
                &mut rng,
                soft,
            );
            let color = if color.finite() {
                color.clamp(0.0, MAX_RADIANCE)
            } else {
                Vec3::ZERO
            };
            let i = ((y * w + x) * 3) as usize;
            s.accum[i] += color.x;
            s.accum[i + 1] += color.y;
            s.accum[i + 2] += color.z;
        }
    }
}

// --- exports ----------------------------------------------------------------

#[no_mangle]
pub extern "C" fn init(max_pixels: u32) {
    let s = state();
    let n = (max_pixels.max(1) as usize).min(960 * 540);
    s.accum = vec![0.0; n * 3];
    s.output = vec![0; n * 4];
    s.max_pixels = n;
    s.spp = 0;
    s.key = 0;
    s.width = 0;
    s.height = 0;
    set_defaults(&mut s.params);
}

#[no_mangle]
pub extern "C" fn reset_defaults() {
    set_defaults(&mut state().params);
}

#[no_mangle]
pub extern "C" fn params_ptr() -> *mut f32 {
    state().params.as_mut_ptr()
}

#[no_mangle]
pub extern "C" fn param_len() -> u32 {
    PARAM_LEN as u32
}

#[no_mangle]
pub extern "C" fn trace(samples: u32) -> u32 {
    let samples = samples.clamp(1, 16);
    let s = state();
    if s.max_pixels == 0 {
        return 0;
    }
    prepare_frame(s);
    let scene = build_scene(&s.params);
    let cam = build_camera(&s.params, s.width, s.height);
    for _ in 0..samples {
        let index = s.spp;
        let soft = index > 0;
        render_sample(&scene, cam, s, index, soft);
        s.spp += 1;
    }
    tonemap(s);
    s.spp
}

#[no_mangle]
pub extern "C" fn present() {
    let s = state();
    tonemap(s);
}

#[no_mangle]
pub extern "C" fn output_ptr() -> *const u8 {
    state().output.as_ptr()
}

#[no_mangle]
pub extern "C" fn frame_width() -> u32 {
    state().width
}

#[no_mangle]
pub extern "C" fn frame_height() -> u32 {
    state().height
}

#[no_mangle]
pub extern "C" fn sample_count() -> u32 {
    state().spp
}

pub fn params() -> &'static mut [f32] {
    &mut state().params
}

pub fn output_slice() -> &'static [u8] {
    let (ptr, n) = {
        let s = state();
        let n = (s.width as usize) * (s.height as usize) * 4;
        (s.output.as_ptr(), n)
    };
    unsafe { std::slice::from_raw_parts(ptr, n) }
}

// --- tests ------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn box_front_and_miss() {
        let ro = Vec3::new(0.0, 0.0, 5.0);
        let rd = Vec3::new(0.0, 0.0, -1.0);
        let (t, n) = intersect_box(ro, rd, 1.0).unwrap();
        assert!((t - 4.0).abs() < 1e-4);
        assert!((n.z - 1.0).abs() < 1e-4);
        assert!(intersect_box(Vec3::new(3.0, 0.0, 0.0), Vec3::new(1.0, 0.0, 0.0), 1.0).is_none());
    }

    #[test]
    fn fresnel_and_tir() {
        let f = fresnel_dielectric(1.0, 1.0, 1.5);
        assert!((f - 0.04).abs() < 1e-3, "normal fresnel {f}");
        assert_eq!(fresnel_dielectric(0.5, 1.5, 1.0), 1.0);
        let n = Vec3::new(0.0, 1.0, 0.0);
        let i = Vec3::new(0.0, -1.0, 0.0);
        let r = refract(i, n, 1.0 / 1.5).unwrap();
        assert!(r.y < -0.99);
        assert!(refract(Vec3::new(0.866, -0.5, 0.0).normalized(), n, 1.5).is_none());
    }

    #[test]
    fn frame_is_lit() {
        init(96 * 64);
        params()[WIDTH] = 96.0;
        params()[HEIGHT] = 64.0;
        let spp = trace(2);
        assert_eq!(spp, 2);
        let px = output_slice();
        assert_eq!(px.len(), 96 * 64 * 4);
        let mut sum = 0u64;
        let mut min_l = u32::MAX;
        let mut max_l = 0u32;
        for chunk in px.chunks_exact(4) {
            let l = chunk[0] as u32 + chunk[1] as u32 + chunk[2] as u32;
            sum += l as u64;
            min_l = min_l.min(l);
            max_l = max_l.max(l);
        }
        let mean = sum as f32 / (96.0 * 64.0 * 3.0);
        assert!(mean > 15.0 && mean < 230.0, "mean byte {mean}");
        assert!(max_l > min_l + 80, "flat image min {min_l} max {max_l}");
        assert!(
            px.iter().any(|b| *b > 180),
            "expected a bright lamp or highlight"
        );
    }
}
