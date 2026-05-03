(function () {
    const TAG = "[GPT-STREAM]";

    // Stop interceptor cũ nếu đang tồn tại
    try {
        if (window.__gptRawInterceptor?.installed && typeof window.__gptRawInterceptor.stop === "function") {
            window.__gptRawInterceptor.stop();
        }
    } catch (e) {
        console.warn(`${TAG} Cannot stop old raw interceptor:`, e);
    }

    if (window.__gptStreamInspector?.installed) {
        console.warn(`${TAG} Interceptor đã tồn tại. Gọi window.__gptStreamInspector.stop() trước nếu muốn cài lại.`);
        return;
    }

    const originalFetch = window.fetch.bind(window);

    const CONFIG = {
        LOG_RESPONSE_META: true,
        LOG_RAW_CHUNK: false,
        LOG_SSE_EVENT: true,
        LOG_JSON: true,
        LOG_TEXT_UPDATE: true,
        USE_COLLAPSED_GROUP: true,
        PREVIEW_LIMIT: 220
    };

    const state = {
        installed: true,
        requestCounter: 0,
        records: [],
        requests: new Map(),
        originalFetch,
        config: CONFIG
    };

    function nowIso() {
        return new Date().toISOString();
    }

    function preview(value, limit = CONFIG.PREVIEW_LIMIT) {
        let text = "";

        try {
            text = typeof value === "string" ? value : JSON.stringify(value);
        } catch {
            text = String(value);
        }

        if (text.length <= limit) return text;
        return text.slice(0, limit) + ` ... [truncated ${text.length - limit} chars]`;
    }

    function logGroup(title, callback) {
        if (CONFIG.USE_COLLAPSED_GROUP && console.groupCollapsed) {
            console.groupCollapsed(title);
            try {
                callback();
            } finally {
                console.groupEnd();
            }
        } else {
            console.log(title);
            callback();
        }
    }

    function getFetchUrl(input) {
        if (typeof input === "string") return input;
        if (input instanceof URL) return input.href;
        if (input instanceof Request) return input.url;
        return String(input ?? "");
    }

    function getFetchMethod(input, init) {
        if (init?.method) return init.method;
        if (input instanceof Request && input.method) return input.method;
        return "GET";
    }

    function shouldIntercept(urlText) {
        try {
            const url = new URL(urlText, window.location.origin);

            // Chỉ bắt đúng endpoint conversation chính.
            // Không bắt /backend-api/f/conversation/prepare
            return url.pathname === "/backend-api/f/conversation";
        } catch {
            return urlText.includes("/backend-api/f/conversation") &&
                !urlText.includes("/backend-api/f/conversation/prepare");
        }
    }

    function safeJsonParse(text) {
        try {
            return {
                ok: true,
                value: JSON.parse(text)
            };
        } catch (error) {
            return {
                ok: false,
                error
            };
        }
    }

    function createRequestState(reqId, url, method) {
        return {
            reqId,
            url,
            method,
            doc: {},
            activeAppendPath: null,
            latestText: "",
            eventCount: 0,
            chunkCount: 0,
            completed: false
        };
    }

    function decodePointerPart(part) {
        return part.replace(/~1/g, "/").replace(/~0/g, "~");
    }

    function splitJsonPointer(path) {
        if (!path || path === "") return [];
        if (!path.startsWith("/")) return [];
        return path
            .slice(1)
            .split("/")
            .map(decodePointerPart);
    }

    function getAtPath(root, path) {
        const parts = splitJsonPointer(path);
        let current = root;

        for (const part of parts) {
            if (current == null) return undefined;

            if (Array.isArray(current)) {
                const index = Number(part);
                current = current[index];
            } else {
                current = current[part];
            }
        }

        return current;
    }

    function ensureContainer(parent, key, nextKey) {
        if (parent[key] == null || typeof parent[key] !== "object") {
            const nextIsIndex = String(Number(nextKey)) === nextKey;
            parent[key] = nextIsIndex ? [] : {};
        }

        return parent[key];
    }

    function setAtPath(rootHolder, path, value) {
        const parts = splitJsonPointer(path);

        if (parts.length === 0) {
            rootHolder.doc = value;
            return;
        }

        let current = rootHolder.doc;

        for (let i = 0; i < parts.length - 1; i += 1) {
            const key = parts[i];
            const nextKey = parts[i + 1];

            if (Array.isArray(current)) {
                const index = Number(key);
                if (current[index] == null || typeof current[index] !== "object") {
                    const nextIsIndex = String(Number(nextKey)) === nextKey;
                    current[index] = nextIsIndex ? [] : {};
                }
                current = current[index];
            } else {
                current = ensureContainer(current, key, nextKey);
            }
        }

        const lastKey = parts[parts.length - 1];

        if (Array.isArray(current)) {
            current[Number(lastKey)] = value;
        } else {
            current[lastKey] = value;
        }
    }

    function appendAtPath(rootHolder, path, value) {
        const currentValue = getAtPath(rootHolder.doc, path);

        if (typeof currentValue === "string") {
            setAtPath(rootHolder, path, currentValue + String(value));
            return;
        }

        if (Array.isArray(currentValue)) {
            currentValue.push(value);
            return;
        }

        if (currentValue == null) {
            setAtPath(rootHolder, path, String(value));
            return;
        }

        // Fallback nếu type lạ
        setAtPath(rootHolder, path, String(currentValue) + String(value));
    }

    function getAssistantText(reqState) {
        const parts = getAtPath(reqState.doc, "/message/content/parts");

        if (Array.isArray(parts)) {
            return parts.join("");
        }

        const part0 = getAtPath(reqState.doc, "/message/content/parts/0");

        if (typeof part0 === "string") {
            return part0;
        }

        return "";
    }

    function summarizeDelta(data) {
        if (data == null) return {};

        if (typeof data !== "object") {
            return {
                value_type: typeof data,
                value_preview: preview(data)
            };
        }

        const summary = {};

        if ("type" in data) summary.type = data.type;
        if ("event" in data) summary.event = data.event;
        if ("o" in data) summary.o = data.o;
        if ("p" in data) summary.p = data.p;

        if ("v" in data) {
            summary.v_type = Array.isArray(data.v) ? "array" : typeof data.v;

            if (typeof data.v === "string") {
                summary.v_preview = preview(data.v, 120);
                summary.v_length = data.v.length;
            } else if (Array.isArray(data.v)) {
                summary.v_length = data.v.length;
                summary.v_ops = data.v.map((op) => {
                    if (op && typeof op === "object") {
                        return {
                            o: op.o,
                            p: op.p,
                            v_type: Array.isArray(op.v) ? "array" : typeof op.v,
                            v_preview: typeof op.v === "string" ? preview(op.v, 80) : undefined
                        };
                    }
                    return {
                        value_type: typeof op
                    };
                });
            } else if (data.v && typeof data.v === "object") {
                summary.v_keys = Object.keys(data.v);
            }
        }

        return summary;
    }

    function applyOperation(reqState, op) {
        if (!op || typeof op !== "object") return;

        const operation = op.o;
        const path = typeof op.p === "string" ? op.p : reqState.activeAppendPath;
        const value = op.v;

        if (operation === "add") {
            setAtPath(reqState, path ?? "", value);
            reqState.activeAppendPath = typeof path === "string" ? path : null;
            return;
        }

        if (operation === "replace") {
            if (typeof path === "string") {
                setAtPath(reqState, path, value);
            }
            return;
        }

        if (operation === "append") {
            if (typeof path === "string") {
                appendAtPath(reqState, path, value);
                reqState.activeAppendPath = path;
            }
            return;
        }

        if (operation === "patch" && Array.isArray(value)) {
            for (const childOp of value) {
                applyOperation(reqState, childOp);
            }
            return;
        }

        // Quan trọng:
        // Với delta_encoding v1, nhiều event sau append chỉ gửi { v: "..." }
        // Không có o/p. Khi đó tiếp tục append vào activeAppendPath trước đó.
        if (!operation && typeof value === "string" && typeof reqState.activeAppendPath === "string") {
            appendAtPath(reqState, reqState.activeAppendPath, value);
        }
    }

    function updateText(reqState) {
        const text = getAssistantText(reqState);

        if (text && text !== reqState.latestText) {
            reqState.latestText = text;

            if (CONFIG.LOG_TEXT_UPDATE) {
                console.log(`${TAG} ${reqState.reqId} TEXT_UPDATE len=${text.length}`);
                console.log(text);
            }
        }
    }

    function addRecord(record) {
        state.records.push(record);
        return record;
    }

    function handleSseEvent(reqState, rawEventText) {
        reqState.eventCount += 1;

        const eventIndex = reqState.eventCount;
        const lines = rawEventText.split(/\r?\n/);

        let eventName = "";
        const dataLines = [];
        const otherLines = [];

        for (const line of lines) {
            if (line.startsWith("event:")) {
                eventName = line.slice(6).trim();
            } else if (line.startsWith("data: ")) {
                dataLines.push(line.slice(6));
            } else if (line.startsWith("data:")) {
                dataLines.push(line.slice(5));
            } else if (line.trim()) {
                otherLines.push(line);
            }
        }

        const dataText = dataLines.join("\n");

        const baseRecord = {
            tag: TAG,
            reqId: reqState.reqId,
            time: nowIso(),
            kind: "sse_event",
            eventIndex,
            eventName,
            dataText,
            rawEventText,
            otherLines
        };

        if (dataText === "[DONE]") {
            reqState.completed = true;

            addRecord({
                ...baseRecord,
                done: true
            });

            console.log(`${TAG} ${reqState.reqId} SSE #${eventIndex} DONE`);
            console.log(`${TAG} ${reqState.reqId} FINAL_TEXT len=${reqState.latestText.length}`);
            console.log(reqState.latestText);
            return;
        }

        const parsed = safeJsonParse(dataText);

        if (!parsed.ok) {
            const record = addRecord({
                ...baseRecord,
                done: false,
                jsonParseError: String(parsed.error)
            });

            logGroup(`${TAG} ${reqState.reqId} SSE #${eventIndex} NON_JSON ${preview(dataText)}`, () => {
                console.log("record:", record);
                console.log("rawEventText:", rawEventText);
                console.warn("jsonParseError:", parsed.error);
            });

            return;
        }

        const data = parsed.value;
        const summary = summarizeDelta(data);

        const record = addRecord({
            ...baseRecord,
            done: false,
            json: data,
            summary
        });

        if (CONFIG.LOG_SSE_EVENT || CONFIG.LOG_JSON) {
            logGroup(`${TAG} ${reqState.reqId} SSE #${eventIndex} ${eventName || "message"} ${preview(summary)}`, () => {
                console.log("summary:", summary);
                console.log("record:", record);

                if (CONFIG.LOG_SSE_EVENT) {
                    console.log("rawEventText:", rawEventText);
                    console.log("dataText:", dataText);
                }

                if (CONFIG.LOG_JSON) {
                    console.log("json:", data);
                }
            });
        }

        // Chỉ apply các delta/patch có dạng object.
        // Những event metadata như input_message, message_marker, server_ste_metadata sẽ không ảnh hưởng text.
        if (data && typeof data === "object") {
            applyOperation(reqState, data);
            updateText(reqState);
        }
    }

    async function inspectResponseStream(reqState, response) {
        const clone = response.clone();

        if (!clone.body) {
            const text = await clone.text().catch((error) => {
                console.warn(`${TAG} ${reqState.reqId} Cannot read response text:`, error);
                return "";
            });

            addRecord({
                tag: TAG,
                reqId: reqState.reqId,
                time: nowIso(),
                kind: "non_stream_response",
                text
            });

            console.log(`${TAG} ${reqState.reqId} NON_STREAM_RESPONSE`, text);
            return;
        }

        const reader = clone.body.getReader();
        const decoder = new TextDecoder();

        let buffer = "";

        while (true) {
            const { done, value } = await reader.read();

            if (done) {
                const tail = decoder.decode();

                if (tail) {
                    buffer += tail;
                }

                if (buffer.trim()) {
                    handleSseEvent(reqState, buffer);
                    buffer = "";
                }

                console.log(`${TAG} ${reqState.reqId} STREAM_END`);
                return;
            }

            reqState.chunkCount += 1;

            const chunkText = decoder.decode(value, { stream: true });

            addRecord({
                tag: TAG,
                reqId: reqState.reqId,
                time: nowIso(),
                kind: "raw_chunk",
                chunkIndex: reqState.chunkCount,
                chunkText
            });

            if (CONFIG.LOG_RAW_CHUNK) {
                logGroup(`${TAG} ${reqState.reqId} CHUNK #${reqState.chunkCount} ${preview(chunkText)}`, () => {
                    console.log("chunkText:", chunkText);
                });
            }

            buffer += chunkText;

            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() ?? "";

            for (const rawEventText of events) {
                if (!rawEventText.trim()) continue;
                handleSseEvent(reqState, rawEventText);
            }
        }
    }

    window.fetch = async function interceptedFetch(...args) {
        const input = args[0];
        const init = args[1];

        const url = getFetchUrl(input);
        const method = getFetchMethod(input, init);

        const response = await originalFetch(...args);

        if (!shouldIntercept(url)) {
            return response;
        }

        state.requestCounter += 1;

        const reqId = `GPT_REQ_${state.requestCounter}`;
        const reqState = createRequestState(reqId, url, method);

        state.requests.set(reqId, reqState);

        const meta = {
            tag: TAG,
            reqId,
            time: nowIso(),
            kind: "response_meta",
            method,
            url,
            status: response.status,
            statusText: response.statusText,
            headers: Object.fromEntries(response.headers.entries())
        };

        addRecord(meta);

        if (CONFIG.LOG_RESPONSE_META) {
            logGroup(`${TAG} ${reqId} RESPONSE_META ${method} ${response.status} ${url}`, () => {
                console.log("meta:", meta);
            });
        }

        inspectResponseStream(reqState, response).catch((error) => {
            console.error(`${TAG} ${reqId} inspectResponseStream error:`, error);
        });

        return response;
    };

    window.__gptStreamInspector = {
        installed: true,
        config: CONFIG,
        records: state.records,
        requests: state.requests,

        stop() {
            window.fetch = originalFetch;
            this.installed = false;
            state.installed = false;
            console.warn(`${TAG} stopped. window.fetch restored.`);
        },

        clear() {
            state.records.length = 0;
            state.requests.clear();
            console.log(`${TAG} records cleared.`);
        },

        dump() {
            console.log(`${TAG} records:`, state.records);
            return state.records;
        },

        getRequest(reqId) {
            const reqState = state.requests.get(reqId);
            console.log(`${TAG} ${reqId}:`, reqState);
            return reqState;
        },

        getText(reqId) {
            const reqState = state.requests.get(reqId);
            const text = reqState?.latestText ?? "";
            console.log(`${TAG} ${reqId} text:`);
            console.log(text);
            return text;
        },

        getLatestText() {
            const values = Array.from(state.requests.values());
            const latest = values[values.length - 1];
            const text = latest?.latestText ?? "";
            console.log(`${TAG} latest text:`);
            console.log(text);
            return text;
        },

        find(keyword) {
            const key = String(keyword);

            const result = state.records.filter((record) => {
                try {
                    return JSON.stringify(record).includes(key);
                } catch {
                    return false;
                }
            });

            console.log(`${TAG} find("${key}") result:`, result);
            return result;
        },

        exportJson() {
            const serializable = {
                records: state.records,
                requests: Array.from(state.requests.values()).map((req) => ({
                    reqId: req.reqId,
                    url: req.url,
                    method: req.method,
                    latestText: req.latestText,
                    eventCount: req.eventCount,
                    chunkCount: req.chunkCount,
                    completed: req.completed,
                    doc: req.doc
                }))
            };

            const text = JSON.stringify(serializable, null, 2);

            if (typeof copy === "function") {
                copy(text);
                console.log(`${TAG} exported JSON copied to clipboard.`);
            } else {
                console.log(`${TAG} exported JSON:`, text);
            }

            return text;
        }
    };

    console.log(`${TAG} installed.`);
    console.log(`${TAG} Filter console bằng keyword: GPT-STREAM hoặc GPT_REQ_1`);
    console.log(`${TAG} Xem text mới nhất: window.__gptStreamInspector.getLatestText()`);
    console.log(`${TAG} Xem request cụ thể: window.__gptStreamInspector.getRequest("GPT_REQ_1")`);
    console.log(`${TAG} Export JSON: window.__gptStreamInspector.exportJson()`);
    console.log(`${TAG} Stop: window.__gptStreamInspector.stop()`);
})();