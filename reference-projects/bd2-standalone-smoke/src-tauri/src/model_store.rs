use serde::Serialize;
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::fs::{self, File, OpenOptions};
use fs2::FileExt;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, atomic::{AtomicU64, Ordering}};
use tauri::{AppHandle, Manager};

// Todas las operaciones de modelo se serializan entre las ventanas del mismo proceso.
// Las instancias de proceso distintas quedan protegidas por un bloqueo de sistema de archivos.
pub(crate) struct ModelGuard(pub Mutex<()>);

#[derive(Serialize)]
pub(crate) struct ModelReply {
    value: Value,
    etag: String,
}

fn checked_id(id: &str) -> Result<(), String> {
    if id.is_empty() || id.len() > 120 || !id.as_bytes()[0].is_ascii_alphanumeric()
        && !matches!(id.as_bytes()[0], b'_' | b'-')
        || !id.bytes().all(|b| b.is_ascii_alphanumeric() || matches!(b, b'_' | b'-' | b'.')) {
        return Err("Invalid model identifier".into());
    }
    let basename = id.split('.').next().unwrap_or("").to_ascii_uppercase();
    if matches!(basename.as_str(), "CON" | "PRN" | "AUX" | "NUL" | "COM1" | "COM2" | "COM3" | "COM4" | "COM5" | "COM6" | "COM7" | "COM8" | "COM9" | "LPT1" | "LPT2" | "LPT3" | "LPT4" | "LPT5" | "LPT6" | "LPT7" | "LPT8" | "LPT9") {
        return Err("Reserved platform model identifier".into());
    }
    Ok(())
}

fn root(app: &AppHandle) -> Result<PathBuf, String> {
    let location = app.path().app_local_data_dir().map_err(|_| "User data directory unavailable")?;
    fs::create_dir_all(&location).map_err(|_| "Cannot create application data directory")?;
    if fs::symlink_metadata(&location).map_err(|_| "Cannot examine user data directory")?.file_type().is_symlink() {
        return Err("Unsafe user data directory".into());
    }
    let models = location.join("models");
    fs::create_dir_all(&models).map_err(|_| "Cannot create model directory")?;
    let meta = fs::symlink_metadata(&models).map_err(|_| "Cannot examine model directory")?;
    if meta.file_type().is_symlink() || !meta.is_dir() {
        return Err("Unsafe model directory".into());
    }
    Ok(models)
}

fn model_path(app: &AppHandle, id: &str) -> Result<PathBuf, String> {
    checked_id(id)?;
    let target = root(app)?.join(format!("{id}.model.json"));
    if let Ok(meta) = fs::symlink_metadata(&target) {
        if !meta.is_file() || meta.file_type().is_symlink() {
            return Err("Unsafe model location".into());
        }
    }
    Ok(target)
}

// Inter-process OS file lock: serialized access survives multiple launched instances.
// The OS releases this advisory lock if a process crashes. Never delete this lock file.
fn lock_store(app: &AppHandle) -> Result<File, String> {
    let file = OpenOptions::new().read(true).write(true).create(true)
        .open(root(app)?.join(".hydra-models.lock"))
        .map_err(|_| "Cannot open model storage lock")?;
    file.lock_exclusive().map_err(|_| "Cannot acquire cross-process model lock")?;
    Ok(file)
}

// Windows' std::fs::rename cannot reliably replace an existing destination.
// MoveFileExW performs an in-place same-volume replacement; never delete the
// previous revision first. An error leaves the old revision untouched.
#[cfg(windows)]
fn commit_revision(staging: &Path, destination: &Path, exists: bool) -> std::io::Result<()> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::{
        MoveFileExW, MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH,
    };
    let from: Vec<u16> = staging.as_os_str().encode_wide().chain(Some(0)).collect();
    let to: Vec<u16> = destination.as_os_str().encode_wide().chain(Some(0)).collect();
    let flags = MOVEFILE_WRITE_THROUGH |
        if exists { MOVEFILE_REPLACE_EXISTING } else { 0 };
    let result = unsafe { MoveFileExW(from.as_ptr(), to.as_ptr(), flags) };
    if result == 0 { Err(std::io::Error::last_os_error()) } else { Ok(()) }
}

#[cfg(not(windows))]
fn commit_revision(staging: &Path, destination: &Path, _exists: bool) -> std::io::Result<()> {
    fs::rename(staging, destination)
}

fn digest(bytes: &[u8]) -> String { format!("{:x}", Sha256::digest(bytes)) }

fn read_value(path: &Path) -> Result<(Value, String), String> {
    let meta = fs::symlink_metadata(path).map_err(|_| "Model not found")?;
    if meta.file_type().is_symlink() || !meta.is_file() { return Err("Unsafe model file".into()); }
    if meta.len() > 2_097_152 { return Err("Model exceeds configured limit".into()); }
    let bytes = fs::read(path).map_err(|_| "Could not read model")?;
    let value: Value = serde_json::from_slice(&bytes).map_err(|_| "Invalid persisted JSON model")?;
    Ok((value, digest(&bytes)))
}

#[tauri::command]
pub(crate) fn hydra_list_models(app: AppHandle, state: tauri::State<'_, ModelGuard>) -> Result<Vec<String>, String> {
    let _locked = state.0.lock().map_err(|_| "Model storage lock poisoned")?;
    let _cross_process = lock_store(&app)?;
    let mut ids = Vec::new();
    for item in fs::read_dir(root(&app)?).map_err(|_| "Could not enumerate models")? {
        let item = item.map_err(|_| "Could not enumerate model")?;
        if !item.file_type().map_err(|_| "Could not inspect model")?.is_file() { continue; }
        let name = item.file_name();
        if let Some(id) = name.to_str().and_then(|n| n.strip_suffix(".model.json")) {
            if checked_id(id).is_ok() { ids.push(id.to_string()); }
        }
    }
    ids.sort();
    Ok(ids)
}

#[tauri::command]
pub(crate) fn hydra_read_model(app: AppHandle, state: tauri::State<'_, ModelGuard>, id: String) -> Result<ModelReply, String> {
    let _locked = state.0.lock().map_err(|_| "Model storage lock poisoned")?;
    let _cross_process = lock_store(&app)?;
    let (value, etag) = read_value(&model_path(&app, &id)?)?;
    Ok(ModelReply { value, etag })
}

#[tauri::command]
pub(crate) fn hydra_write_model(app: AppHandle, state: tauri::State<'_, ModelGuard>, id: String,
    body: String, if_match: Option<String>, if_none_match: bool) -> Result<ModelReply, String> {
    let _locked = state.0.lock().map_err(|_| "Model storage lock poisoned")?;
    let _cross_process = lock_store(&app)?;
    let path = model_path(&app, &id)?;
    write_value(&path, &id, &body, if_match.as_deref(), if_none_match)
}

fn write_value(path: &Path, id: &str, body: &str,
    if_match: Option<&str>, if_none_match: bool) -> Result<ModelReply, String> {
    checked_id(id)?;
    if body.len() > 2_097_152 { return Err("Model exceeds configured limit".into()); }
    let value: Value = serde_json::from_str(&body).map_err(|_| "Invalid JSON model")?;
    if value.as_object().and_then(|v| v.get("id")).and_then(Value::as_str) != Some(id.as_str()) {
        return Err("Model JSON id differs from requested id".into());
    }
    let exists = path.exists();
    if exists && if_none_match || !exists && if_match.is_some() {
        return Err("Concurrent model change".into());
    }
    if exists {
        let (_, current) = read_value(&path)?;
        let supplied = if_match.unwrap_or("").trim_matches('"');
        if supplied != current { return Err("Concurrent model change: If-Match required".into()); }
    } else if !if_none_match {
        return Err("Create requires If-None-Match".into());
    }
    let parent = path.parent().ok_or("No model directory")?;
    let mut last_error = "Could not atomically persist model";
    // create_new prevents overwriting a staging file from a concurrent or crashed process.
    static NEXT_STAGING: AtomicU64 = AtomicU64::new(0);
    for _attempt in 0..16 {
        let staging = parent.join(format!(".hydra-{}-{}.tmp", std::process::id(),
            NEXT_STAGING.fetch_add(1, Ordering::Relaxed)));
        let Ok(mut stream) = OpenOptions::new().write(true).create_new(true).open(&staging) else {continue};
        let written = stream.write_all(body.as_bytes()).and_then(|_| stream.sync_all());
        drop(stream);
        if written.is_err() {
            let _ = fs::remove_file(&staging);
            return Err("Could not safely write new model revision".into());
        }
        if commit_revision(&staging, &path, exists).is_ok() {
            return Ok(ModelReply {value, etag:digest(body.as_bytes())});
        }
        let _ = fs::remove_file(&staging);
        last_error = "Could not atomically persist model (existing data preserved)";
        break;
    }
    Err(last_error.into())
}

#[tauri::command]
pub(crate) fn hydra_delete_model(app: AppHandle, state: tauri::State<'_, ModelGuard>, id: String,
    if_match: Option<String>) -> Result<(), String> {
    let _locked = state.0.lock().map_err(|_| "Model storage lock poisoned")?;
    let _cross_process = lock_store(&app)?;
    let path = model_path(&app, &id)?;
    delete_value(&path, if_match.as_deref())
}

fn delete_value(path: &Path, if_match: Option<&str>) -> Result<(), String> {
    let (_, current) = read_value(path)?;
    if if_match.unwrap_or("").trim_matches('"') != current {
        return Err("Concurrent model change: If-Match required".into());
    }
    fs::remove_file(path).map_err(|_| "Cannot delete model".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temporary_model() -> (PathBuf, PathBuf) {
        let stamp = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        let dir = std::env::temp_dir().join(format!("hydra-native-tests-{}-{}",std::process::id(),stamp));
        fs::create_dir(&dir).unwrap();
        (dir.clone(), dir.join("alpha.model.json"))
    }

    #[test]
    fn creates_updates_and_reloads_exact_model_revision() {
        let (dir, model) = temporary_model();
        let bytes1 = r#"{"id":"alpha","title":"one"}"#;
        let bytes2 = r#"{"id":"alpha","title":"two"}"#;
        let first = write_value(&model,"alpha",bytes1,None,true).unwrap();
        assert_eq!(first.etag,digest(bytes1.as_bytes()));
        assert_eq!(read_value(&model).unwrap().0["title"],"one");
        let second = write_value(&model,"alpha",bytes2,Some(&first.etag),false).unwrap();
        assert_eq!(read_value(&model).unwrap().1,second.etag);
        assert_eq!(fs::read_to_string(&model).unwrap(),bytes2);
        fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn stale_edit_and_second_create_cannot_replace_revision() {
        let (dir, model) = temporary_model();
        let bytes=r#"{"id":"alpha","version":1}"#;
        let first=write_value(&model,"alpha",bytes,None,true).unwrap();
        assert!(write_value(&model,"alpha",r#"{"id":"alpha","version":2}"#,Some("stale"),false).is_err());
        assert!(write_value(&model,"alpha",bytes,None,true).is_err());
        assert_eq!(read_value(&model).unwrap().1,first.etag);
        fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn invalid_payload_and_mismatched_identity_cannot_damage_file() {
        let (dir, model) = temporary_model();
        let bytes=r#"{"id":"alpha","value":3}"#;
        let first=write_value(&model,"alpha",bytes,None,true).unwrap();
        assert!(write_value(&model,"alpha","{",Some(&first.etag),false).is_err());
        assert!(write_value(&model,"alpha",r#"{"id":"evil"}"#,Some(&first.etag),false).is_err());
        assert_eq!(fs::read_to_string(&model).unwrap(),bytes);
        fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn conditional_delete_requires_latest_revision() {
        let (dir, model)=temporary_model();
        let first=write_value(&model,"alpha",r#"{"id":"alpha"}"#,None,true).unwrap();
        assert!(delete_value(&model,Some("wrong")).is_err());
        assert!(model.is_file());
        delete_value(&model,Some(&first.etag)).unwrap();
        assert!(!model.exists());
        fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn rejects_unsafe_platform_identifiers() {
        for id in ["../escape","CON","COM1.txt","a/b","a:b",".hidden",""] {
            assert!(checked_id(id).is_err(),"unsafe id: {id}");
        }
        assert!(checked_id("model-42").is_ok());
    }
}

