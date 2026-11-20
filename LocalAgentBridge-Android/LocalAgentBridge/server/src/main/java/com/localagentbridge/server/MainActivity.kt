package com.localagentbridge.server

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : Activity() {
    private lateinit var upstreamField: EditText
    private lateinit var modelField: EditText
    private lateinit var apiKeyField: EditText
    private lateinit var statusText: TextView
    private lateinit var addressText: TextView
    private lateinit var tokenText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.token(this)
        buildScreen()
        loadSettings()
        refreshStatus()
        requestNotificationPermissionIfNeeded()
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFFF7F8FA.toInt())
        }
        val scroll = ScrollView(this).apply { addView(content) }

        content.addView(text("LOCAL AGENT BRIDGE", 22, true))
        content.addView(text("Servidor Android • API na rede local", 14, false).also { it.setTextColor(0xFF5B6472.toInt()) })
        content.addView(spacing(18))
        statusText = text("Status: parado", 16, true)
        content.addView(statusText)
        addressText = text("Endereço: —", 14, false)
        content.addView(addressText)
        content.addView(spacing(10))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("INICIAR SERVIDOR") {
            saveSettings()
            startApiService()
            statusText.text = "Status: iniciando…"
            statusText.postDelayed({ refreshStatus() }, 700)
        }, weighted())
        buttons.addView(button("PARAR") {
            stopService(Intent(this, LocalApiService::class.java))
            statusText.text = "Status: parado"
        }, weighted())
        content.addView(buttons)
        content.addView(spacing(18))

        content.addView(text("TOKEN DE ACESSO", 15, true))
        tokenText = text(AppPrefs.token(this), 12, false)
        tokenText.setTextIsSelectable(true)
        content.addView(tokenText)
        content.addView(button("COPIAR TOKEN") { copy("Token da API", AppPrefs.token(this)) })
        content.addView(button("GERAR NOVO TOKEN") {
            val newToken = AppPrefs.rotateToken(this)
            tokenText.text = newToken
        })
        content.addView(spacing(18))

        content.addView(text("CONFIGURAÇÃO DO MODELO", 15, true))
        content.addView(text("Use um servidor compatível com a API OpenAI. Ex.: Ollama/LM Studio em outro PC da mesma rede.", 13, false))
        upstreamField = field("URL-base (ex.: http://192.168.1.10:11434/v1)")
        content.addView(upstreamField)
        modelField = field("Modelo (ex.: llama3.2, qwen2.5)")
        content.addView(modelField)
        apiKeyField = field("Chave da API (opcional)", password = true)
        content.addView(apiKeyField)
        content.addView(button("SALVAR CONFIGURAÇÃO") {
            saveSettings()
            statusText.text = "Configuração salva."
        })
        content.addView(spacing(18))

        content.addView(text("ENDPOINTS", 15, true))
        content.addView(text("POST  /v1/chat/completions\nGET   /v1/models\nGET   /v1/agent/tools\nPOST  /v1/agent/tools/call\nGET   /v1/config\nGET   /health", 13, false))
        content.addView(button("COPIAR URL DA API") {
            copy("URL da API", "http://${findLocalIp()}:$PORT/v1")
        })
        content.addView(spacing(14))
        content.addView(text("Segurança: use somente em uma rede Wi-Fi confiável. Não configure redirecionamento de portas no roteador. O token protege a API, mas o tráfego HTTP local não é criptografado.", 12, false).also { it.setTextColor(0xFF9A3412.toInt()) })

        setContentView(scroll)
    }

    private fun loadSettings() {
        val prefs = AppPrefs.prefs(this)
        upstreamField.setText(prefs.getString(AppPrefs.KEY_UPSTREAM, ""))
        modelField.setText(prefs.getString(AppPrefs.KEY_MODEL, ""))
        apiKeyField.setText(prefs.getString(AppPrefs.KEY_API_KEY, ""))
        tokenText.text = AppPrefs.token(this)
    }

    private fun saveSettings() {
        AppPrefs.prefs(this).edit()
            .putString(AppPrefs.KEY_UPSTREAM, upstreamField.text.toString().trim().trimEnd('/'))
            .putString(AppPrefs.KEY_MODEL, modelField.text.toString().trim())
            .putString(AppPrefs.KEY_API_KEY, apiKeyField.text.toString().trim())
            .apply()
    }

    private fun startApiService() {
        val intent = Intent(this, LocalApiService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun refreshStatus() {
        val running = AppPrefs.prefs(this).getBoolean(AppPrefs.KEY_RUNNING, false)
        statusText.text = if (running) "Status: servidor ativo na porta $PORT" else "Status: parado ou iniciando"
        addressText.text = "Endereço: http://${findLocalIp()}:$PORT"
        tokenText.text = AppPrefs.token(this)
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) refreshStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501)
        }
    }

    private fun findLocalIp(): String {
        return try {
            val addresses = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap")) 0 else 1 }
                .flatMap { Collections.list(it.inetAddresses) }
                .filter { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            addresses.firstOrNull { it.isSiteLocalAddress }?.hostAddress ?: "IP-DO-CELULAR"
        } catch (_: Exception) {
            "IP-DO-CELULAR"
        }
    }

    private fun copy(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun text(value: String, size: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(0xFF182230.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun field(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        singleLine = true
        setPadding(dp(12), dp(8), dp(12), dp(8))
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = dp(8)
        }
    }

    private fun button(title: String, action: () -> Unit): Button = Button(this).apply {
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

    private fun weighted() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
        it.marginEnd = dp(4)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val PORT = ApiServer.PORT }
}
