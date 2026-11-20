package com.localagentbridge.client

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var serverField: EditText
    private lateinit var tokenField: EditText
    private lateinit var modelField: EditText
    private lateinit var promptField: EditText
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        val prefs = getSharedPreferences("client_settings", Context.MODE_PRIVATE)
        serverField.setText(prefs.getString("server", "http://192.168.1.100:8765"))
        tokenField.setText(prefs.getString("token", ""))
        modelField.setText(prefs.getString("model", ""))
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFFF7F8FA.toInt())
        }
        val scroll = ScrollView(this).apply { addView(content) }
        content.addView(text("LOCAL AGENT BRIDGE", 22, true))
        content.addView(text("Cliente • conecte ao servidor Android pela rede local", 14, false))
        content.addView(spacing(14))
        content.addView(text("Servidor", 15, true))
        serverField = field("http://IP-DO-CELULAR:8765")
        content.addView(serverField)
        tokenField = field("Token de acesso", password = true)
        content.addView(tokenField)
        modelField = field("Modelo (opcional; vazio usa o configurado no servidor)")
        content.addView(modelField)
        content.addView(button("TESTAR CONEXÃO") { saveSettings(); runRequest("health") })
        content.addView(spacing(14))
        content.addView(text("Mensagem para a IA", 15, true))
        promptField = EditText(this).apply {
            hint = "Digite uma mensagem…"
            minLines = 4
            maxLines = 8
            textSize = 15f
            gravity = android.view.Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(promptField)
        content.addView(button("ENVIAR MENSAGEM") { saveSettings(); runRequest("chat") })
        content.addView(spacing(12))
        content.addView(text("RESPOSTA", 15, true))
        resultText = text("A resposta do servidor aparecerá aqui.", 13, false)
        resultText.setTextIsSelectable(true)
        content.addView(resultText)
        setContentView(scroll)
    }

    private fun saveSettings() {
        getSharedPreferences("client_settings", Context.MODE_PRIVATE).edit()
            .putString("server", serverField.text.toString().trim().trimEnd('/'))
            .putString("token", tokenField.text.toString().trim())
            .putString("model", modelField.text.toString().trim())
            .apply()
    }

    private fun runRequest(action: String) {
        val base = serverField.text.toString().trim().trimEnd('/')
        val token = tokenField.text.toString().trim()
        if (base.isBlank()) {
            resultText.text = "Informe o endereço do servidor."
            return
        }
        resultText.text = "Conectando…"
        val prompt = promptField.text.toString()
        val modelText = modelField.text.toString().trim()
        Thread {
            val result = try {
                if (action == "health") request("$base/health", null, null)
                else {
                    if (token.isBlank()) throw IllegalArgumentException("Informe o token de acesso.")
                    val payload = JSONObject()
                        .put("messages", JSONArray().put(
                            JSONObject().put("role", "user").put("content", prompt)
                        ))
                        .put("stream", false)
                    val model = modelText
                    if (model.isNotBlank()) payload.put("model", model)
                    request("$base/v1/chat/completions", token, payload.toString())
                }
            } catch (error: Exception) {
                "Erro: ${error.message ?: "falha de conexão"}"
            }
            runOnUiThread { resultText.text = result }
        }.start()
    }

    private fun request(url: String, token: String?, body: String?): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 130_000
                requestMethod = if (body == null) "GET" else "POST"
                setRequestProperty("Accept", "application/json")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "Sem conteúdo"
            return "HTTP $status\n\n$response"
        } finally {
            connection?.disconnect()
        }
    }

    private fun text(value: String, size: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(0xFF182230.toInt())
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun field(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        textSize = 14f
        singleLine = true
        setPadding(dp(12), dp(8), dp(12), dp(8))
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = dp(7)
        }
    }

    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = dp(5)
        }
    }

    private fun spacing(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
