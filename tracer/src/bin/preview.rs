//! Render stills of each system so the picture can be checked without a browser.
//! `cargo run --release --bin preview -- /tmp/cubelight`

use cubelight::*;
use std::env;
use std::fs;
use std::time::Instant;

fn main() {
    let dir = env::args()
        .nth(1)
        .unwrap_or_else(|| "/tmp/cubelight".into());
    fs::create_dir_all(&dir).unwrap();
    init(800 * 450);
    params()[WIDTH] = 800.0;
    params()[HEIGHT] = 450.0;

    let jobs = [
        ("whitted", 1.0, 10u32),
        ("direct", 0.0, 4),
        ("path", 2.0, 8),
        ("normals", 3.0, 1),
        ("depth", 4.0, 1),
        ("ao", 5.0, 1),
        ("albedo", 6.0, 1),
    ];
    for (name, system, spp) in jobs {
        params()[SYSTEM] = system;
        let t = Instant::now();
        let got = trace(spp);
        let ms = t.elapsed().as_secs_f32() * 1000.0;
        let (w, h, bytes) = {
            let px = output_slice();
            (frame_width(), frame_height(), px.to_vec())
        };
        let path = format!("{dir}/{name}.ppm");
        write_ppm(&path, w, h, &bytes);
        println!("{name:8} {got:3} spp  {w}x{h}  {ms:7.1} ms  {path}");
    }
}

fn write_ppm(path: &str, w: u32, h: u32, rgba: &[u8]) {
    let mut body = Vec::with_capacity((w * h * 3) as usize);
    for px in rgba.chunks_exact(4) {
        body.extend_from_slice(&px[..3]);
    }
    let mut file = format!("P6\n{w} {h}\n255\n").into_bytes();
    file.extend(body);
    fs::write(path, file).unwrap();
}
