package com.localagentbridge.server

import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiServer {
    const val PORT = 8765
    private var engine: EmbeddedServer<*, *>? = null

    @Synchronized
    fun start(context: Context) {
        if (engine != null) return
        val appContext = context.applicationContext
        engine = embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
            configureRoutes(appContext)
        }.start(wait = false)
    }

    @Synchronized
    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
        engine = null
    }

    private fun Application.configureRoutes(context: Context) {
        routing {
            get("/health") {
                call.respondJson(JSONObject()
                    .put("ok", true)
                    .put("service", "Local Agent Bridge")
                    .put("version", "1.0.0"))
            }

            route("/v1") {
                get("/models") {
                    if (!call.authorized(context)) return@get call.unauthorized()
                    val model = AppPrefs.prefs(context).getString(AppPrefs.KEY_MODEL, "") ?: ""
                    val models = JSONArray().put(JSONObject()
                        .put("id", model.ifBlank { "configured-model" })
                        .put("object", "model")
                        .put("owned_by", "local-agent-bridge"))
                    call.respondJson(JSONObject().put("object", "list").put("data", models))
                }

                get("/config") {
                    if (!call.authorized(context)) return@get call.unauthorized()
                    val prefs = AppPrefs.prefs(context)
                    call.respondJson(JSONObject()
                        .put("base_url", "http://<IP-DO-CELULAR>:$PORT/v1")
                        .put("upstream_base_url", prefs.getString(AppPrefs.KEY_UPSTREAM, "") ?: "")
                        .put("model", prefs.getString(AppPrefs.KEY_MODEL, "") ?: "")
                        .put("api_key_configured", !prefs.getString(AppPrefs.KEY_API_KEY, "").isNullOrBlank()))
                }

                get("/agent/tools") {
                    if (!call.authorized(context)) return@get call.unauthorized()
                    val tools = JSONArray()
                        .put(toolDefinition("device_info", "Retorna modelo, fabricante e versão Android do celular."))
                        .put(toolDefinition("echo", "Devolve o texto enviado em arguments.value."))
                    call.respondJson(JSONObject().put("tools", tools))
                }

                post("/agent/tools/call") {
                    if (!call.authorized(context)) return@post call.unauthorized()
                    try {
                        val input = JSONObject(call.receiveText())
                        val name = input.optString("name")
                        val args = input.optJSONObject("arguments") ?: JSONObject()
                        val result = when (name) {
                            "device_info" -> JSONObject()
                                .put("manufacturer", Build.MANUFACTURER)
                                .put("brand", Build.BRAND)
                                .put("model", Build.MODEL)
                                .put("android_version", Build.VERSION.RELEASE)
                                .put("sdk", Build.VERSION.SDK_INT)
                                .put("uptime_ms", SystemClock.elapsedRealtime())
                            "echo" -> JSONObject().put("value", args.opt("value") ?: JSONObject.NULL)
                            else -> {
                                call.respondJson(
                                    JSONObject().put("error", JSONObject().put("message", "Ferramenta desconhecida: $name")),
                                    HttpStatusCode.NotFound
                                )
                                return@post
                            }
                        }
                        call.respondJson(JSONObject().put("ok", true).put("result", result))
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "JSON inválido"), HttpStatusCode.BadRequest)
                    }
                }

                post("/chat/completions") {
                    if (!call.authorized(context)) return@post call.unauthorized()
                    try {
                        val input = JSONObject(call.receiveText())
                        if (input.optBoolean("stream", false)) {
                            call.respondJson(
                                errorJson("Streaming ainda não está habilitado. Envie stream=false."),
                                HttpStatusCode.BadRequest
                            )
                            return@post
                        }

                        val prefs = AppPrefs.prefs(context)
                        val upstream = (prefs.getString(AppPrefs.KEY_UPSTREAM, "") ?: "").trim().trimEnd('/')
                        val configuredModel = (prefs.getString(AppPrefs.KEY_MODEL, "") ?: "").trim()
                        val apiKey = (prefs.getString(AppPrefs.KEY_API_KEY, "") ?: "").trim()
                        if (upstream.isBlank()) {
                            call.respondJson(
                                errorJson("Configure a URL-base do modelo na tela do app Servidor."),
                                HttpStatusCode.ServiceUnavailable
                            )
                            return@post
                        }
                        if (!input.has("model") || input.optString("model").isBlank()) {
                            if (configuredModel.isNotBlank()) input.put("model", configuredModel)
                        }

                        val response = withContext(Dispatchers.IO) {
                            forwardChat(upstream, apiKey, input.toString())
                        }
                        call.respondText(
                            response.body,
                            ContentType.Application.Json,
                            HttpStatusCode.fromValue(response.status)
                        )
                    } catch (error: Exception) {
                        call.respondJson(errorJson(error.message ?: "Falha ao encaminhar solicitação"), HttpStatusCode.BadGateway)
                    }
                }
            }
        }
    }

    private fun toolDefinition(name: String, description: String): JSONObject {
        val properties = if (name == "echo") {
            JSONObject().put("value", JSONObject()
                .put("type", "string")
                .put("description", "Texto que deve ser devolvido"))
        } else JSONObject()
        val required = if (name == "echo") JSONArray().put("value") else JSONArray()
        return JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required)))
    }

    private fun ApplicationCall.authorized(context: Context): Boolean {
        val expected = "Bearer ${AppPrefs.token(context)}"
        return request.headers[HttpHeaders.Authorization] == expected
    }

    private suspend fun ApplicationCall.unauthorized() {
        respondJson(errorJson("Token ausente ou inválido."), HttpStatusCode.Unauthorized)
    }

    private suspend fun ApplicationCall.respondJson(
        value: JSONObject,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        respondText(value.toString(), ContentType.Application.Json, status)
    }

    private fun errorJson(message: String) = JSONObject()
        .put("error", JSONObject().put("message", message).put("type", "local_agent_bridge_error"))

    private data class ProxyResponse(val status: Int, val body: String)

    private fun forwardChat(baseUrl: String, apiKey: String, requestBody: String): ProxyResponse {
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            ProxyResponse(status, body)
        } catch (error: Exception) {
            ProxyResponse(
                502,
                errorJson("Não foi possível acessar o modelo em $endpoint: ${error.message ?: "erro de rede"}").toString()
            )
        } finally {
            connection?.disconnect()
        }
    }
}
