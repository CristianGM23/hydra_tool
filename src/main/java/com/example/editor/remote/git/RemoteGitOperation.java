package com.example.editor.remote.git;

/** Operaciones Git explícitamente admitidas; no se aceptan comandos arbitrarios. */
public enum RemoteGitOperation { STATUS,INIT,FETCH,PULL,PUSH,MERGE,ABORT_MERGE,COMMIT_ALL,CREATE_BRANCH,CHECKOUT }
