#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod model_store;
use model_store::ModelGuard;
use std::sync::Mutex;
fn main() {
    tauri::Builder::default()
        .manage(ModelGuard(Mutex::new(())))
        .invoke_handler(tauri::generate_handler![
            model_store::hydra_list_models,
            model_store::hydra_read_model,
            model_store::hydra_write_model,
            model_store::hydra_delete_model,
        ])
        .run(tauri::generate_context!())
        .expect("HYDRA generated editor startup failed");
}
