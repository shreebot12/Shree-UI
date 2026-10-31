# OpenJarvis audit and Island X Android mapping

## Audit scope

The public [OpenJarvis repository](https://github.com/open-jarvis/OpenJarvis) was cloned and inspected at commit [`570d75425d50592d931bac7a17ce6b2621561c06`](https://github.com/open-jarvis/OpenJarvis/tree/570d75425d50592d931bac7a17ce6b2621561c06). The audit covered the README, architecture/query-flow documentation, Python package tree, dependency manifest, tests, Rust workspace, desktop frontend, and representative agent, engine, tool, memory, skills, speech, and scheduler implementations. It did not review every optional connector, third-party model-weight license, or dependency's individual license.

OpenJarvis is a Python-first personal-AI research and application framework, not an Android app. Its checkout contains a Rust/PyO3 extension and a React/Tauri desktop frontend; it has no Android Gradle module. The root package identifies itself as Apache-2.0. The Tauri Rust manifest separately identifies the desktop component as MIT, so that component was excluded from this port.

No OpenJarvis source code was copied into Island X. This is a functional architecture mapping with new Kotlin/Android implementations. The reference project and audited revision are attributed here; consult its [Apache-2.0 license](https://github.com/open-jarvis/OpenJarvis/blob/570d75425d50592d931bac7a17ce6b2621561c06/LICENSE) before redistributing any source derived from it.

## OpenJarvis architecture

The documented core is organized around Intelligence/model catalog, Engine, Agentic Logic, Memory, and Learning, with typed registries making engines, agents, tools, channels, skills, and other primitives pluggable. `SystemBuilder` composes configuration, model runtime, tools, memory, security, telemetry, sessions, scheduler, and optional speech into a `JarvisSystem`.

### Request and execution flow

1. Configuration and registry lookup select an engine/model and construct runtime services (`src/openjarvis/system/builder.py`, `src/openjarvis/core/registry.py`).
2. The SDK either calls a simple agent or builds a query with session and retrieved memory context (`src/openjarvis/sdk.py`).
3. Tool-using agents advertise schemas to the model. The ReAct agent runs a bounded thought/action/observation loop; the executor resolves registered tools, applies its confirmation/capability hooks, invokes a tool, and returns its observation (`src/openjarvis/agents/native_react.py`, `src/openjarvis/tools/_stubs.py`).
4. The engine adapter calls a local or cloud model. The common engine contract includes generation, streaming, model discovery, and health checks (`src/openjarvis/engine/_stubs.py`).
5. Session, memory, trace, and telemetry services persist or measure results. Fact extraction can run in a background worker; scheduled and continuous agents have separate lifecycle services.

### Major repository modules

| OpenJarvis module | Role in the reference implementation |
|---|---|
| `core`, `intelligence` | Shared types, configuration, event bus, credential handling, registries, and model catalog/routing. |
| `engine` | Provider adapters, including Ollama and OpenAI-compatible/cloud engines; streaming, health, and model discovery contracts. |
| `agents` | Simple chat, orchestrator, native ReAct, research, digest, code-oriented, and long-running/monitor agents. |
| `tools`, `tools/storage` | Schema-based tool execution and tool integrations; local retrieval and storage include SQLite, BM25, dense/vector, and hybrid options. |
| `memory` | Durable facts, extraction, trust/provenance, and a background processing service. This is distinct from document retrieval. |
| `skills` | Skill manifests, parsing/import, indexing, policy/security checks, and skill-to-tool adaptation. |
| `speech` | Microphone/audio I/O, speech recognition providers, and text-to-speech backends. |
| `connectors`, `channels` | Data-source integrations and external messaging channels; many depend on provider credentials/OAuth. |
| `scheduler`, `workflow`, `sessions`, `daemon` | Recurring task execution, workflow graphs, conversation persistence/compression, and long-running process management. |
| `server` | FastAPI service, including an OpenAI-compatible chat-completions route. |
| `telemetry`, `traces`, `analytics`, `evals`, `bench`, `learning`, `operators`, `mining` | Inference measurement, trace capture, evaluation, optimization/training, and research workflows. |
| `sandbox`, `security`, `mcp`, `a2a` | Sandboxed/remote execution, policy and security boundaries, tool protocol adapters, and agent-to-agent interfaces. |
| `rust`, `frontend`, `desktop` | PyO3/Rust acceleration/storage components and the React/Tauri/browser/desktop application surfaces. |

### Dependencies and platform constraints

The Python package targets Python `>=3.10,<3.14`. Core dependencies include Click, `httpx`, `openai`, `croniter`, and related application libraries; optional groups add FastAPI, cloud SDKs, desktop/speech stacks, vector backends, sandbox runtimes, and research/training packages. The Rust workspace uses PyO3, Tokio, `reqwest`, and SQLite. The desktop frontend is not a mobile UI and uses Tauri plugins.

The Ollama adapter expects a running HTTP service (by default on localhost); it is not itself a model runtime. The Python speech/audio dependencies and host-oriented shell, file, Docker, browser, connector, and scheduler integrations cannot simply be bundled into a regular Android app. Android app sandboxing, background-execution restrictions, runtime permissions, and ABI/model-size constraints require different implementations.

## Feature mapping

| Reference concept | Island X Android implementation | Status and boundary |
|---|---|---|
| Engine/provider contract | `ChatCompletionProvider` with `OpenAiCompatibleClient` | Implemented for `/chat/completions` JSON APIs. Supports cloud providers and local services that implement this contract. No provider SDK or key is bundled. |
| Local/on-device model access | OpenAI-compatible endpoint field; loopback/emulator HTTP is permitted without a key | Endpoint client works when a compatible runtime is reachable. Island X does **not** package model weights, an inference runtime, Ollama, llama.cpp, or NPU acceleration; pure on-device generation is not yet provided. |
| Cloud model access | Configurable HTTPS endpoint, model ID, and optional API key | Implemented. The key is AES-GCM encrypted with an Android Keystore key. Messages, selected memories, and enabled skill instructions are sent to the configured endpoint. |
| Agent/query loop | `AssistantRuntime` builds a system context and exchanges model/tool messages | Implemented for multi-turn chat and bounded tool calls. No streaming tokens, agent routing, autonomous ReAct text parser, or multi-agent orchestration yet. |
| Tool registry | Public `AssistantTool` interface and `AssistantToolRegistry` | Implemented and extensible in Kotlin. Built-ins: local time, basic device info, memory search/save, and opening an installed app. |
| Tool approval/security | `requiresApproval` tool metadata and an in-app approve/deny card | Save-memory, device-info disclosure, and open-app calls are blocked until the user approves that specific call. No shell, arbitrary intents, accessibility-driven UI automation, or unrestricted file/network tools are exposed. |
| Durable memory | Room-backed local memories; encrypted payloads; simple ranked term matching | Implemented as user-authored or explicitly approved facts. Relevant facts are selected for the current prompt; the model may also request memory search. This is not OpenJarvis's automatic LLM fact extractor or its embedding/vector document store. |
| Skills | Local, editable skill name/description/instructions with enable/delete controls | Implemented as encrypted prompt guidance, not executable scripts or imported community packages. Tool calls suggested by skills remain subject to the same approval gate. |
| Voice | Android `RecognizerIntent` speech input and Android `TextToSpeech` reply playback | Implemented in the foreground UI; listening status is sent to the Island overlay. Recognition behavior and whether audio is processed locally depend on the Android speech service selected by the device; Island X does not record or persist audio. |
| Dynamic Island | Assistant status/reply events flow through Island X `EventHub` and the existing overlay | Implemented when the Island X overlay is enabled. Expanding the assistant event exposes an action to reopen chat. Replies are shortened for the capsule. |
| Ongoing/background work | In-app coroutine, busy/approval status event | Foreground chat work only. It is not a durable Android background task and may stop if Android kills the process. |
| Scheduler/workflows | None in this port | OpenJarvis's daemon-thread scheduler and DAG engine need Android-specific WorkManager/foreground-service design and user controls before porting. |
| Retrieval/connectors/channels | None in this port | No calendar, mail, contacts, browser, messaging, OAuth, or external connector access is enabled. |
| Traces, telemetry, learning/evals | None in this port | No prompt/response analytics or model-training feedback is transmitted by Island X. |

## Privacy and execution model

- Chat messages, memory values, skill instructions, and the optional provider key are encrypted individually with AES-GCM and a non-exportable Android Keystore key before persistence. Database structure and some metadata (message roles/timestamps, skill names/descriptions, and record IDs) are not encrypted. Android backup is disabled for this app. If the Keystore key becomes unavailable, encrypted data cannot be recovered; the app reports decryption errors instead of silently substituting plaintext.
- Cloud requests are sent only after the user configures an endpoint and model. Remote endpoints must use HTTPS. HTTP is allowed only for `localhost`, `127.0.0.1`, and emulator host `10.0.2.2`, and API keys are refused over HTTP.
- Voice recognition is an explicit user action. Audio is handed to Android's recognition activity; only the returned text is stored in the encrypted chat database.
- Android actions run through ordinary platform APIs. Calls that alter app/device state require a per-call confirmation; Android may still refuse actions because of platform policy or permissions.
- Assistant requests run while the app process is alive; Island overlay events are a display surface, not a guarantee of background execution.

## Validation and known gaps

Build and unit-test results are recorded in [DEVELOPMENT_STATUS.md](../DEVELOPMENT_STATUS.md). Local Gradle compilation, unit tests, and APK assembly validate the implementation in the repository; they do not prove compatibility with every model provider, Android speech service, OEM overlay, or physical device. No live model endpoint/API key was supplied, so end-to-end remote inference and physical-device approval/voice/overlay behavior remain unverified.
