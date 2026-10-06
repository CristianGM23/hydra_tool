package com.example.editor.web;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte mensajes técnicos en explicaciones orientadas a la corrección del
 * formulario, diagrama o conexión. El contrato HTTP conserva códigos estables
 * y el detalle técnico separado; la interfaz puede localizar y traducir estos
 * campos sin mostrar respuestas crudas del proveedor.
 */
final class ApiErrorAdvice {
    private static final Pattern HTTP_STATUS = Pattern.compile("\\bHTTP\\s+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);

    record Details(String field, String reason, String solution, List<String> suggestions) {
        Details {
            field = normalize(field);
            reason = normalize(reason);
            solution = normalize(solution);
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    /** Descripción: Inicializa una instancia de ApiErrorAdvice con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ApiErrorAdvice() {}

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de Details. Entradas: code: dato de entrada necesario para la operación; technicalMessage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Details from(String code, String technicalMessage) { return from(code, technicalMessage, ""); }

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de Details. Entradas: code: dato de entrada necesario para la operación; technicalMessage: dato de entrada necesario para la operación; explicitField: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Details from(String code, String technicalMessage, String explicitField) {
        String message = normalize(technicalMessage);
        String lower = message.toLowerCase(Locale.ROOT);
        String normalizedCode = normalize(code).toUpperCase(Locale.ROOT);
        String field = explicitField.isBlank() ? inferField(message) : explicitField;

        Details remote = remoteAdvice(normalizedCode, message, lower, field);
        if(remote != null) return remote;

        if(lower.startsWith("remote repository or folder url")) {
            if(lower.contains("required")) return new Details("repositoryUrl",
                    "The repository or folder address is empty, so HYDRA has no destination to recognize.",
                    "Copy the HTTPS URL from the repository, project, folder or document page in the external service and paste it into Repository or folder URL. Then press Recognize destination before saving the connection.",
                    List.of("Use the page URL from GitHub, GitLab, OneDrive/SharePoint, Google Drive/Docs or Dropbox; do not paste an API token."));
            if(lower.contains("must use https")) return new Details("repositoryUrl",
                    "The supplied address is not an HTTPS URL. HYDRA rejects non-HTTPS repository and cloud-storage destinations.",
                    "Open the destination in the provider, copy its https:// address and paste that complete URL into Repository or folder URL. Do not replace HTTPS with HTTP to bypass this check.",
                    List.of("For a self-managed GitLab or Git server, configure a trusted HTTPS endpoint first."));
            if(lower.contains("embedded credentials")) return new Details("repositoryUrl",
                    "The URL contains a username, password or token. HYDRA does not accept credentials embedded in repository URLs.",
                    "Remove the credential portion from the URL and paste only the normal HTTPS destination address. Connect the account later in the Access step so passwords, MFA and OAuth secrets stay on the provider page or secure server configuration.",
                    List.of("Use https://host/path, never https://user:password@host/path."));
            if(lower.contains("contain a host") || lower.contains("invalid")) return new Details("repositoryUrl",
                    "The repository or folder address is incomplete or malformed, so HYDRA cannot identify the external service.",
                    "Paste the complete HTTPS URL including the provider host and destination path. For example, use https://github.com/organization/repository instead of only https://. Then press Recognize destination.",
                    List.of("GitHub needs owner/repository; GitLab needs namespace/project; cloud providers need the folder, file or sharing URL."));
        }
        if(lower.contains("github url must identify a repository")) return new Details("repositoryUrl",
                "The GitHub address identifies github.com but does not identify a repository.",
                "Open the repository in GitHub and copy the complete URL containing both owner or organization and repository name, for example https://github.com/acme/orders. Paste it again and press Recognize destination.",
                List.of("A GitHub home, organization or profile URL is not enough."));
        if(lower.contains("gitlab url must identify a project")) return new Details("repositoryUrl",
                "The GitLab address does not contain a complete project path.",
                "Open the project in GitLab and copy the URL containing its complete namespace and project name, for example https://gitlab.com/group/project. Paste it again and press Recognize destination.",
                List.of("Group-only and GitLab home URLs cannot be used as repository destinations."));

        if(lower.contains("stable document id")) return new Details(field,
                "A document ID must begin with a letter or number, contain only letters, numbers, dots, underscores or hyphens, and contain at most 120 characters. Spaces, colons and slashes are not allowed.",
                "Edit the highlighted ID field and use a stable value such as action-model, people.events or validation_rules. Keep this ID unchanged after other elements reference it.",
                List.of("Remove spaces and punctuation such as ':' or '/'.", "Use the visible Name field for a human-readable label."));
        if(lower.contains("stable element id")) return new Details(field,
                "An element ID must begin with a letter or number. It may contain letters, numbers, dots, underscores, hyphens and colons, up to 240 characters.",
                "Replace the value with a stable element identifier such as rule:unique-name or event:model-saved.",
                List.of("Do not use spaces.", "Do not change the ID after bindings or mappings reference it."));
        if(lower.contains("path") && lower.contains("document id") && lower.contains("must match")) return new Details(field,
                "The identifier in the URL and the identifier entered in the form refer to different documents.",
                "Reload the document and save it under the ID shown in the current tab, or create a new document instead of renaming a persisted ID.",
                List.of("Use the Name field to rename what users see.", "Keep persisted technical IDs stable."));
        if(lower.contains("too long") || lower.contains("too many")) return new Details(field,
                "The submitted value exceeds the safety budget accepted by this form or request.",
                "Shorten the value or reduce the number of submitted elements. Large imports should use the dedicated upload/import workflow instead of a normal form field.",
                List.of("Remove duplicated or generated text.", "Use a file/import operation for large structured content."));
        if(lower.contains("control characters")) return new Details(field,
                "The value contains non-printable control characters that can corrupt files, logs or downstream parsers.",
                "Remove the hidden control characters and enter the value again using normal text characters.",
                List.of("Paste through a plain-text editor if the source may contain hidden characters."));
        if(lower.contains("executable markup") || lower.contains("unsafe path") || lower.contains("encoded control sequence")) return new Details(field,
                "HYDRA rejected a potentially executable or unsafe input sequence.",
                "Use plain descriptive text and a path/identifier that stays inside the selected HYDRA resource. Executable markup is not accepted in descriptive fields.",
                List.of("Do not embed scripts, event-handler attributes or credentials in text fields.", "Use the dedicated expression/code editor when executable DSL content is intended."));
        if(lower.contains("required") || lower.contains("must not be blank") || lower.contains("must not be empty")) return new Details(field,
                "A mandatory value is empty or was not selected.",
                "Complete the indicated field before saving. Open its information button to see the expected value and an example.",
                List.of("Review every field marked with *.", "Select a referenced element before completing dependent fields."));
        if(lower.contains("not found")) return new Details(field,
                "The form or diagram references a document or element that is not currently available in this context.",
                "Open the path or element shown in the diagnostic. Create/save the missing dependency first, or remove the stale reference and choose an existing element from the selector.",
                List.of("Reload the selector after saving the dependency.", "Check that referenced documents belong to the same metamodel/project."));
        if(lower.contains("duplicate") || lower.contains("already exists")) return new Details(field,
                "Another document or element already uses the same stable identifier.",
                "Choose a different technical ID or edit the existing element instead of creating a duplicate.",
                List.of("Keep visible names descriptive, but IDs unique within their document."));
        if("INVALID_JSON".equals(normalizedCode) || lower.contains("json")) return new Details(field,
                "The submitted form could not be converted to the HYDRA document because one value has the wrong type, format or structure.",
                "Review the indicated field, correct it in the structured form and submit again. Avoid editing raw JSON unless the Source tab explicitly requires it.",
                List.of("Use the field tooltip to check the expected format.", "Restore the last valid value if the error appeared after a manual JSON edit."));
        if("PRECONDITION_FAILED".equals(normalizedCode)) return new Details(field,
                "The document changed after it was loaded, so applying this operation could overwrite newer data.",
                "Reload the affected document, review the newer version, reapply your intended change and save again.",
                List.of("Do not retry blindly when another tab or user may have changed the same resource."));
        if("CONFLICT".equals(normalizedCode)) return new Details(field,
                "The requested change conflicts with data that already exists or with the current document state.",
                "Open the element or path identified by the error, resolve the duplicate or incompatible state, then retry the operation.",
                List.of("Reload before retrying if the resource may have changed elsewhere."));
        if("STORAGE_ERROR".equals(normalizedCode)) return new Details(field,
                "HYDRA could not safely read or persist the requested resource.",
                "Verify that the project location is accessible and writable, then retry. If the problem persists, check the server log using the correlation ID without exposing credentials.",
                List.of("Check free disk space and file permissions.", "Make sure another process is not locking the file."));
        if("INTERNAL_ERROR".equals(normalizedCode)) return new Details(field,
                "An unexpected HYDRA backend error stopped the operation before a reliable result could be returned.",
                "Retry once. If it repeats, use the correlation ID shown by HYDRA to locate the server log entry and fix the underlying backend error before retrying again.",
                List.of("Do not copy passwords, OAuth codes or tokens into issue reports."));
        String subject = subject(message);
        String concrete = subject.isBlank() ? "" : " The involved value/reference is '" + subject + "'.";
        if(lower.contains("cardinal") || lower.contains("multiplicit") || lower.contains("lower bound") || lower.contains("upper bound") || lower.contains("collection")) return new Details(field,
                "The configured multiplicity cannot safely represent the values required by this operation." + concrete,
                "Compare the source and target minimum/maximum bounds. If a collection feeds a single-valued target, configure an explicit reduction; if the target is mandatory, guarantee at least one value. Re-run the validation with zero, one and multiple values.",
                List.of("Correct the exact field/path reported by the error; do not change unrelated cardinalities."));
        if(lower.contains("mapping") || lower.contains("binding") || lower.contains("correspondence") || lower.contains("source feature") || lower.contains("target feature") || lower.contains("reverse rule") || lower.contains("forward rule")) return new Details(field,
                "The mapping or correspondence is incomplete or inconsistent with its source/target endpoints." + concrete,
                "Open the reported rule or binding and verify, in order, source type, target type, source feature, target feature, direction and conversion. For a bidirectional correspondence, create the reverse rule with the same correspondence ID and swap source/target only when the mappings are actually reversible.",
                List.of("Resolve the first invalid rule/binding and validate again before editing downstream mappings."));
        if(lower.contains("cycle") || lower.contains("cyclic") || lower.contains("loop")) return new Details(field,
                "The reported references or links form a cycle that this operation does not allow." + concrete,
                "Follow the IDs in the reported path until they return to the first element, then remove or redirect only the link that closes the cycle. Validate again to confirm the remaining graph is still reachable.",
                List.of("Do not delete every element in the cycle; fix the closing relation first."));
        if(lower.contains("syntax") || lower.contains("parse") || lower.contains("parser") || lower.contains("grammar") || lower.contains("compile") || lower.contains("expression") || lower.contains("operator") || lower.contains("datatype") || lower.contains("conversion")) return new Details(field,
                "HYDRA could not parse, type-check or compile the reported content." + concrete + " Concrete detail: " + message,
                "Open the exact field/path reported by the error and correct the first syntax or semantic failure there before addressing later messages, because they may be cascading diagnostics. Check symbol names, delimiters, operators and input/target types, then run validation again.",
                List.of("Review a proposed quick fix before applying it; do not suppress a type error by weakening unrelated validation."));
        return new Details(field,
                "The operation rejected the current value." + concrete + (message.isBlank() ? "" : " Concrete detail: " + message),
                "Open the exact field, element or path identified by the error, compare its current value with the concrete detail above, change only the related property and retry the same operation. If an ID or path is present, use it to navigate directly to the failing element instead of reviewing the whole document.",
                List.of("Keep the error code and reported path/ID when reproducing the issue; they identify the validation that failed.", "Do not assume a partial change was saved when the operation failed."));
    }

    /** Descripción: Ejecuta la operación «subject» correspondiente a la responsabilidad de ApiErrorAdvice. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String subject(String message) {
        if(message == null || message.isBlank()) return "";
        Matcher quoted = Pattern.compile("[\"'`](.{1,120}?)[\"'`]").matcher(message);
        if(quoted.find()) return normalize(quoted.group(1));
        Matcher id = Pattern.compile("\\b(?:id|rule|binding|feature|node|edge|type|class|field|property)\\s*[=:]\\s*([A-Za-z0-9_.:-]{1,120})", Pattern.CASE_INSENSITIVE).matcher(message);
        return id.find() ? normalize(id.group(1)) : "";
    }

    /** Descripción: Ejecuta la operación «remoteAdvice» correspondiente a la responsabilidad de Details. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; lower: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Details remoteAdvice(String code, String message, String lower, String field) {
        String provider = provider(lower);
        int status = httpStatus(message);
        if(provider.isEmpty() && !isRemoteCode(code)) return null;

        if(lower.contains("aadsts50059")) return new Details("oauth-microsoft-organization",
                "Microsoft cannot determine which Microsoft organization should authorize this HYDRA application, so sign-in cannot start.",
                "Open Installation setup for OneDrive. If the Entra app is limited to one organization, choose Only this organization and paste its organization directory ID or verified organization domain from Entra > App registrations > your app > Overview. If the app accepts accounts from several organizations, choose the matching shared account type instead. Save the setup and press Connect account again.",
                List.of("Do not paste the Application (client) ID into the Organization identifier field.", "The account type selected in HYDRA must match Supported account types in the Entra app."));
        if(lower.contains("aadsts50194")) return new Details("oauth-microsoft-organization",
                "The Microsoft app accepts one organization, but HYDRA is attempting to authorize it through a sign-in mode intended for several organizations.",
                "In Installation setup choose Only this organization and enter that app's organization directory ID or verified domain. Alternatively change Supported account types in Entra so the app accepts accounts from several organizations, and then select the same account type in HYDRA.",
                List.of("After changing Entra registration settings, save HYDRA setup before reconnecting."));
        if(lower.contains("aadsts90002")) return new Details("oauth-microsoft-organization",
                "Microsoft cannot find the organization configured for this OneDrive connection.",
                "Copy the organization directory ID or verified organization domain directly from the Entra app Overview page, replace the Organization identifier value in Installation setup, save it and reconnect the account.",
                List.of("Check for copied spaces or an organization identifier taken from a different Entra application."));

        if(lower.contains("device flow") && lower.contains("github") && (lower.contains("disabled") || lower.contains("device_flow_disabled"))) return new Details("deviceFlow",
                "Device Flow is disabled in the GitHub OAuth App used by this HYDRA installation.",
                "Open GitHub > Settings > Developer settings > OAuth Apps > the HYDRA app and enable Device Flow. Keep the same Client ID in HYDRA, save Installation setup, then press Connect account again. If you prefer browser authorization, disable device sign-in in HYDRA and configure the matching client secret on the HYDRA server.",
                List.of("The user's GitHub password and MFA are entered only on github.com."));
        if(provider.equals("github") && (lower.contains("incorrect_client_credentials") || lower.contains("invalid_client") || lower.contains("rejected the oauth application identity"))) return new Details("clientId",
                "GitHub does not recognize the OAuth application identity configured in HYDRA.",
                "Copy the Client ID from the exact GitHub OAuth App used for this installation and save it in Installation setup. For browser authorization, also configure that app's matching client secret on the HYDRA server; for Device Flow, enable Device Flow in the same OAuth App.",
                List.of("Do not use a repository ID, account name or client secret in the Client ID field."));
        if(provider.equals("github") && (lower.contains("redirect") || lower.contains("callback"))) return new Details("callbackUrl",
                "GitHub rejected HYDRA's callback URL because it does not match the OAuth App registration.",
                "Copy the callback URL displayed by HYDRA and register that exact scheme, host, port and path in the GitHub OAuth App. Save the provider setup and reconnect.",
                List.of("http and https are different callback URLs.", "A changed port or context path also requires updating the OAuth App."));

        if(provider.equals("gitlab") && (lower.contains("device authorization") || lower.contains("device flow") || lower.contains("authorize_device") || lower.contains("unsupported_grant_type"))) return new Details("authorizationMode",
                "The selected GitLab server does not support the requested Device Authorization flow.",
                "Switch this connection to browser authorization with PKCE. If Device Authorization is required, upgrade/use a GitLab version that supports it and enable the corresponding application grant before retrying.",
                List.of("For self-managed GitLab, verify the OAuth endpoints belong to the configured server base URL."));
        if(provider.equals("gitlab") && (lower.contains("invalid_client") || lower.contains("unauthorized_client") || lower.contains("rejected the oauth application identity"))) return new Details("clientId",
                "GitLab rejected the Application ID/client authentication configured for HYDRA.",
                "Copy the Application ID from GitLab > Applications. For HYDRA's PKCE public-client flow, configure the GitLab application so confidential client authentication is not required. If your registration is confidential, place its matching secret only on the HYDRA server.",
                List.of("Confirm the callback URL and scopes belong to the same GitLab application."));
        if(provider.equals("gitlab") && lower.contains("invalid_scope")) return new Details("scopes",
                "GitLab rejected at least one OAuth scope requested by HYDRA.",
                "Open the GitLab application, enable the repository/API scopes required by this profile, then make the HYDRA scope list match those enabled permissions and reconnect.",
                List.of("Use the minimum permissions needed for the configured repository operations."));
        if(provider.equals("gitlab") && lower.contains("redirect")) return new Details("callbackUrl",
                "GitLab rejected HYDRA's redirect URI because the registered callback does not match.",
                "Copy the callback URL shown in HYDRA Installation setup and register it exactly in the GitLab application, including scheme, host, port and path. Save and reconnect.", List.of());

        if(provider.equals("google drive") && (lower.contains("redirect_uri_mismatch") || lower.contains("redirect uri mismatch") || lower.contains("rejected the redirect uri"))) return new Details("callbackUrl",
                "Google rejected the OAuth redirect URI or the OAuth client type does not match this HYDRA deployment.",
                "For localhost/loopback use a Desktop OAuth client. For a hosted HYDRA server use a Web application OAuth client, add the exact callback URL shown by HYDRA to Authorized redirect URIs, and configure the matching client secret only on the HYDRA server. Save the setup and reconnect.",
                List.of("The callback must match exactly; scheme, host, port and path all matter."));
        if(provider.equals("google drive") && (lower.contains("invalid_client") || lower.contains("oauth client was deleted") || lower.contains("rejected the oauth client"))) return new Details("clientId",
                "Google does not recognize the OAuth client configured for this HYDRA installation.",
                "Copy the Client ID from Google Cloud Console > APIs & Services > Credentials for the OAuth client created for HYDRA. If it is a hosted Web application, configure the matching client secret on the HYDRA server and ensure the exact callback URL is registered.",
                List.of("Do not use an API key as the OAuth Client ID."));
        if(provider.equals("google drive") && lower.contains("org_internal")) return new Details("account",
                "The Google OAuth application is restricted to an organization that does not include the account being used.",
                "Sign in with an account inside the allowed Google Workspace organization, or change the OAuth consent screen audience so the intended account is allowed, then reconnect.", List.of());
        if(provider.equals("google drive") && lower.contains("admin_policy_enforced")) return new Details("scopes",
                "Google Workspace policy has blocked this application or one of the Drive permissions it requests.",
                "Ask the Workspace administrator to allow the HYDRA OAuth application and its required Drive scopes in Admin Console, then reconnect. If a blocked scope is optional for your profile, remove it from the HYDRA provider setup.", List.of());
        if(provider.equals("google drive") && lower.contains("invalid_scope")) return new Details("scopes",
                "Google rejected one or more OAuth scopes configured for Drive.",
                "Enable the Google Drive API in the same Cloud project, configure the consent screen for the requested scopes, remove unsupported scopes from HYDRA, save and reconnect.", List.of());
        if(provider.equals("google drive") && lower.contains("invalid_grant")) return new Details("account",
                "Google rejected the stored authorization grant because it expired, was revoked or no longer matches the OAuth client.",
                "Disconnect the Google Drive account in HYDRA and connect it again. If the OAuth client was recreated, update its Client ID/callback/client secret first, then complete consent again on Google's page.", List.of());

        if(provider.equals("dropbox") && (lower.contains("redirect_uri_mismatch") || lower.contains("rejected the redirect uri"))) return new Details("callbackUrl",
                "Dropbox rejected HYDRA's redirect URI because it is not registered exactly for this app.",
                "Open the Dropbox App Console, add the exact callback URL displayed by HYDRA to Redirect URIs, save the app, then reconnect the account.",
                List.of("Keep the same App key in HYDRA after updating the redirect URI."));
        if(provider.equals("dropbox") && (lower.contains("invalid_client") || lower.contains("app key") || lower.contains("rejected the application identity"))) return new Details("clientId",
                "Dropbox does not recognize the App key/application identity configured in HYDRA.",
                "Copy the App key from the Dropbox App Console for the app registered for this HYDRA installation, save Installation setup and reconnect.", List.of());
        if(provider.equals("dropbox") && lower.contains("scope")) return new Details("scopes",
                "Dropbox rejected the requested permissions or the token does not include the permissions needed by this connection.",
                "Enable the required permissions on the Dropbox app Permissions tab, keep HYDRA scopes consistent with them, submit the Dropbox app changes, then disconnect and reconnect so a new token receives those permissions.", List.of());

        if(provider.equals("github") && (lower.contains("expired_token") || lower.contains("incorrect_device_code") || lower.contains("bad_verification_code"))) return new Details("account",
                "The GitHub device authorization code is no longer valid, so this sign-in attempt cannot be completed.",
                "Return to HYDRA and press Connect account again to obtain a new device code. Open the GitHub verification page shown by HYDRA, enter the new code and finish sign-in before its expiry time. Do not reuse a code from an earlier attempt.",
                List.of("If GitHub says the code is invalid immediately, verify that Device Flow is enabled in the same OAuth App whose Client ID is configured in HYDRA."));
        if(provider.equals("gitlab") && lower.contains("invalid_grant")) return new Details("account",
                "GitLab rejected the authorization grant because it expired, was already used, was revoked or does not match the current PKCE authorization attempt.",
                "Start Connect account again from HYDRA and complete the newly opened GitLab authorization without reusing an old callback or browser tab. If the OAuth application was changed, verify its callback URL and Application ID first, then reconnect.",
                List.of("For self-managed GitLab, make sure the browser and HYDRA are using the same GitLab base URL."));
        if(provider.equals("dropbox") && lower.contains("invalid_grant")) return new Details("account",
                "Dropbox rejected the authorization grant because it is expired, revoked, already consumed or no longer matches this app configuration.",
                "Disconnect the Dropbox account in HYDRA and connect it again. If the Dropbox app was recreated or its redirect URI changed, update the App key and exact Redirect URI in Installation setup before reconnecting.",
                List.of("Complete authorization in the newest Dropbox window; do not reuse an older callback tab."));
        if(lower.contains("authorization was cancelled") || lower.contains("authorization_declined") || lower.contains("access_denied")) return new Details("account",
                "The provider sign-in was cancelled or the user denied the requested authorization.",
                "Press Connect account again, sign in on the provider page and approve the requested permissions. If you do not want to grant them, reduce the configured scopes to only those required by the repository operations you intend to use.", List.of());

        if(lower.contains("timed out") || lower.contains("timeout") || lower.contains("read timed out") || lower.contains("connect timed out")) return new Details(field.isBlank()?"providerUrl":field,
                providerLabel(provider)+" did not respond before the connection timeout expired.",
                "First open the provider in the same network/browser to confirm it is reachable. Then verify the provider/server URL in HYDRA, the corporate proxy/VPN and firewall rules. Retry Test connection once network access is restored; do not change OAuth credentials merely because a network timeout occurred.",
                List.of("For self-managed GitLab, confirm the configured server URL and port are reachable from the HYDRA server.", "If a proxy is required, configure it for the HYDRA server process as well as the browser."));
        if(lower.contains("unknownhost") || lower.contains("unknown host") || lower.contains("name or service not known") || lower.contains("temporary failure in name resolution") || lower.contains("dns")) return new Details(field.isBlank()?"providerUrl":field,
                "HYDRA cannot resolve the hostname of the configured external service.",
                "Open the connection profile and verify the provider/server hostname exactly. If it is correct, check DNS resolution from the machine running HYDRA, then verify VPN/proxy settings. For self-managed GitLab, use the real externally resolvable base URL rather than an internal alias that the HYDRA server cannot resolve.",
                List.of("Do not replace a valid HTTPS hostname with a raw IP unless the provider and its TLS certificate are explicitly configured for that IP."));
        if(lower.contains("connection refused") || lower.contains("connectexception") || lower.contains("failed to connect")) return new Details(field.isBlank()?"providerUrl":field,
                "The configured external service address was reached, but no service accepted the connection on that host and port.",
                "Verify the provider/server URL and port, confirm the service is running, and check firewall, reverse-proxy and container port mappings. For hosted providers, retry after checking their service status; for self-managed GitLab, test the exact base URL from the HYDRA server machine.",
                List.of("A browser working on your workstation does not prove that the HYDRA server can reach the same host."));
        if(lower.contains("pkix") || lower.contains("sslhandshake") || lower.contains("certificate") && (lower.contains("trust") || lower.contains("unable to find valid certification path"))) return new Details(field.isBlank()?"providerUrl":field,
                "HYDRA could reach the external HTTPS service, but it could not establish a trusted TLS connection.",
                "Check that the configured HTTPS hostname matches the certificate and that the certificate chain is valid. For a self-managed service using an internal CA, install that CA certificate in the trust store used by the HYDRA Java runtime, then restart HYDRA and run Test connection again. Do not disable TLS certificate verification.",
                List.of("Check certificate expiry and intermediate certificates on the server or reverse proxy."));
        if(lower.contains("proxy") && (lower.contains("407") || lower.contains("authentication required") || lower.contains("connect failed"))) return new Details("proxy",
                "The network proxy required by this HYDRA installation rejected or could not relay the external connection.",
                "Configure the HYDRA server process with the correct proxy host, port and—if required—proxy credentials using the installation's secure runtime configuration. Verify that the proxy allows HTTPS access to the selected provider, then restart/reload HYDRA if required and run Test connection again.",
                List.of("Do not enter proxy passwords into repository or OAuth fields."));

        if(status > 0) {
            if(status == 401) return new Details(field.isBlank()?"account":field,
                    providerLabel(provider)+" rejected the current authorization token or session.",
                    "Disconnect this external account in HYDRA and connect it again. Complete sign-in/MFA on the provider page, then run Test connection. If it still fails, verify that the OAuth application and repository profile refer to the same provider and, for Microsoft, the same organization.", List.of());
            if(status == 403) return new Details(field.isBlank()?"permissions":field,
                    providerLabel(provider)+" understood the request but the connected account or OAuth token is not allowed to perform it.",
                    permissionSolution(provider), List.of("Verify that the connected account itself can open the target repository/folder in the provider UI.", "After changing scopes or organization policy, reconnect so HYDRA receives a new token."));
            if(status == 404) return new Details(field.isBlank()?remoteLocationField(provider):field,
                    "The target repository, branch, drive, folder or file could not be found with the current connection.",
                    notFoundSolution(provider), List.of("Check capitalization and copied URLs/IDs.", "Confirm that the connected account can see the target in the provider UI."));
            if(status == 409 || status == 412) return new Details(field,
                    "The remote resource changed or conflicts with the version HYDRA loaded previously.",
                    "Refresh/import the remote resource again, compare the newer version with your local changes, resolve the conflict, and then save/export again. Do not overwrite blindly.", List.of());
            if(status == 429) return new Details(field,
                    providerLabel(provider)+" is rate-limiting this connection or its configured quota has been reached.",
                    "Wait for the provider retry window, reduce repeated connection tests/operations and retry. If this continues, review API quota/rate-limit settings for the OAuth application and account.", List.of());
            if(status >= 500) return new Details(field,
                    providerLabel(provider)+" returned a server-side error while HYDRA was performing the remote operation.",
                    "Keep the local project unchanged and retry after the provider recovers. If the error persists, check the provider status/service health and then verify the connection again before retrying a write.", List.of());
            if(status == 400) return new Details(field,
                    providerLabel(provider)+" rejected the request because one of the repository coordinates, OAuth parameters or operation values is invalid.",
                    "Open the repository profile and re-check the provider URL, repository/project/drive identifier, branch, remote root and OAuth setup. Correct the field named by the error, save the profile, reconnect if OAuth settings changed, and run Test connection again.", List.of());
        }
        if(code.equals("CREDENTIALS_MISSING") || code.equals("REFRESH_REQUIRED") || code.equals("CONNECTION_NOT_FOUND")) return new Details("account",
                "This remote profile does not currently have a usable provider authorization.",
                "Open the repository profile, press Connect account, finish sign-in and MFA on the provider page, return to HYDRA and run Test connection. HYDRA does not need or store your provider password.", List.of());
        if(code.equals("PROVIDER_NOT_CONFIGURED")) return new Details("providerSetup",
                "The provider application for this HYDRA installation is incomplete or inconsistent.",
                "Open Installation setup, correct the provider-specific Client/Application ID, callback, organization identifier when Microsoft requires one, and permissions shown by HYDRA. Save it, and only then press Connect account. Secrets required by hosted server flows must be configured on the HYDRA server, never in the browser form.", List.of());
        if(code.equals("VAULT_UNAVAILABLE")) return new Details("credentialVault",
                "HYDRA cannot safely persist the OAuth credential because secure credential storage is not initialized.",
                "Initialize/configure the HYDRA credential vault on the server, restart/reload the installation as required, then connect the account again. Do not replace the vault with plain-text token storage.", List.of());
        if(code.equals("FLOW_EXPIRED")) return new Details("account",
                "The external authorization attempt expired before the provider completed it.",
                "Press Connect account again and complete provider sign-in/consent in the newly opened window before the code expires. Use the safe fallback link if the browser blocks the popup.", List.of());
        if(code.equals("TOKEN_EXCHANGE_FAILED")) return new Details(field.isBlank()?"account":field,
                "The external service was reached, but HYDRA could not complete the final authorization exchange and the provider did not return a more specific recognizable cause.",
                "Open Installation setup for this provider and verify that the Application/Client ID belongs to the same registered app as the callback shown by HYDRA. Then start Connect account again and complete the newest sign-in window. If the provider requires a server-side app secret, verify that secret in the HYDRA server configuration rather than entering it in the browser.",
                List.of("Do not reuse an old authorization callback or device code.", "If the problem repeats, use the technical detail and correlation ID to inspect the provider response without exposing tokens."));
        if(code.equals("REMOTE_ERROR")) return new Details(field.isBlank()?"remoteLocation":field,
                "HYDRA could not complete communication with the remote destination, and the returned error does not identify a more specific authorization, permission, missing-resource or quota problem.",
                "Return to this repository connection and verify the process in order: paste the complete destination URL, press Recognize destination and confirm the detected provider; save the profile; for OneDrive, GitHub, GitLab, Google Drive or Dropbox make sure Access shows the account as connected; then run Test connection. If Test connection still fails, check the provider service/network and use the technical detail or correlation ID to identify the exact server response before changing OAuth settings.",
                List.of("Confirm that the same account can open the target repository or folder in the provider UI.", "Check VPN, proxy, firewall and provider service status if the destination is correct.", "Do not rotate Client IDs, secrets or passwords unless a later diagnostic specifically identifies authorization or application configuration."));
        return null;
    }

    /** Descripción: Ejecuta la operación «permissionSolution» correspondiente a la responsabilidad de Details. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String permissionSolution(String provider) {
        return switch(provider) {
            case "github" -> "Verify that the connected GitHub account can access the owner/repository and branch. Check the OAuth app authorization, organization SSO/policy and repository permissions; then reconnect and test again.";
            case "gitlab" -> "Verify project membership and that the OAuth application/token includes the API/repository scopes required by this operation. For self-managed GitLab also check project/group policy; then reconnect and test again.";
            case "microsoft onedrive" -> "Verify the account can open the target OneDrive/SharePoint drive and that the Entra application has the Microsoft Graph file permissions required by this profile. If admin consent is required, grant it, reconnect and test again.";
            case "google drive" -> "Enable Google Drive API in the OAuth client's Cloud project, verify the connected account can access the folder, and confirm the requested Drive scopes are allowed by the consent screen/Workspace policy. Reconnect after changing permissions.";
            case "dropbox" -> "Verify the Dropbox app Permissions tab includes the required file scopes and that the connected account can access the target path. Submit permission changes, reconnect the account, and test again.";
            default -> "Verify the connected account can access the target and that the OAuth application grants the permissions required by this operation. Reconnect after changing permissions.";
        };
    }

    /** Descripción: Ejecuta la operación «notFoundSolution» correspondiente a la responsabilidad de Details. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String notFoundSolution(String provider) {
        return switch(provider) {
            case "github" -> "Reopen the repository profile and verify owner, repository name and branch against the GitHub URL. If the repository is private, confirm the connected account is a member/collaborator and then test again.";
            case "gitlab" -> "Verify the GitLab project ID or full namespace/path and branch against the project URL. For self-managed GitLab also verify the configured server base URL, then test again.";
            case "microsoft onedrive" -> "Resolve/paste the OneDrive or SharePoint folder URL again and verify the drive/folder still exists for the connected account. Save the resolved location before testing again.";
            case "google drive" -> "Paste/resolve the Google Drive folder URL or ID again, verify the folder has not been moved/deleted and that the connected account has access, then save and test again.";
            case "dropbox" -> "Paste/resolve the Dropbox shared URL or path again and verify the item is available to the connected account, then save and test again.";
            default -> "Re-enter the remote location from the provider UI, save the profile and test again.";
        };
    }

    /** Descripción: Ejecuta la operación «remoteLocationField» correspondiente a la responsabilidad de Details. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String remoteLocationField(String provider) {
        return switch(provider) { case "github" -> "repository/branch"; case "gitlab" -> "projectId/branch"; case "microsoft onedrive", "google drive" -> "drive/folder"; case "dropbox" -> "rootPath"; default -> "remoteLocation"; };
    }
    /** Descripción: Evalúa la condición «isRemoteCode» sin modificar el estado externo. Entradas: code: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isRemoteCode(String code) { return List.of("REMOTE_ERROR","TOKEN_EXCHANGE_FAILED","PROVIDER_NOT_CONFIGURED","CREDENTIALS_MISSING","REFRESH_REQUIRED","CONNECTION_NOT_FOUND","VAULT_UNAVAILABLE","FLOW_EXPIRED").contains(code); }
    /** Descripción: Ejecuta la operación «provider» correspondiente a la responsabilidad de Details. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String provider(String lower) {
        if(lower.contains("github")) return "github";
        if(lower.contains("gitlab")) return "gitlab";
        if(lower.contains("onedrive") || lower.contains("microsoft") || lower.contains("aadsts")) return "microsoft onedrive";
        if(lower.contains("google drive") || lower.contains("google")) return "google drive";
        if(lower.contains("dropbox")) return "dropbox";
        return "";
    }
    /** Descripción: Ejecuta la operación «providerLabel» correspondiente a la responsabilidad de Details. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String providerLabel(String provider) { return provider.isBlank()?"The external provider":switch(provider){case "github"->"GitHub";case "gitlab"->"GitLab";case "microsoft onedrive"->"Microsoft OneDrive";case "google drive"->"Google Drive";case "dropbox"->"Dropbox";default->provider;}; }
    /** Descripción: Ejecuta la operación «httpStatus» correspondiente a la responsabilidad de Details. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int httpStatus(String message) { Matcher m=HTTP_STATUS.matcher(message); if(!m.find()) return -1; try{return Integer.parseInt(m.group(1));}catch(NumberFormatException ignored){return -1;} }

    /** Descripción: Ejecuta la operación «inferField» correspondiente a la responsabilidad de Details. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String inferField(String message) {
        int marker = message.indexOf(" must ");
        if(marker > 0) {
            String candidate = message.substring(0, marker).trim();
            int separator = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('('));
            if(separator >= 0 && separator + 1 < candidate.length()) candidate = candidate.substring(separator + 1).trim();
            if(candidate.length() <= 80) return candidate;
        }
        return "";
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de Details. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
