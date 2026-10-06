# HYDRA HTTP API compatibility

HYDRA keeps the current `/api/...` routes stable and publishes `X-Hydra-Api-Version` on every API response.
The current contract version is `1`.

A change is considered breaking when it removes or renames an endpoint, makes an optional request field mandatory,
removes a response field, changes the meaning/type of an existing field, or changes concurrency/precondition semantics.
Breaking changes require a new contract version and a compatibility period. Additive optional fields and new endpoints
remain compatible with the current contract.

The `headless-server` and `production` profiles bind to loopback by default while HYDRA remains a single-user system.
External exposure is therefore an explicit deployment choice through `HYDRA_SERVER_ADDRESS` and must sit behind a
trusted network/reverse-proxy boundary until identity and authorization are implemented.
