package com.example.assistente

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var entrada: EditText
    private lateinit var chave: EditText
    private var tts: TextToSpeech? = null
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private val REQ_VOZ = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContentView(montarTela())
        escrever("Assistente pronto. Cole sua chave da API e peça algo, por exemplo: \"me acorda às 6h30\".")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("pt", "BR")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun montarTela(): LinearLayout {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }
        raiz.addView(TextView(this).apply {
            text = "Assistente IA"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })

        chave = EditText(this).apply {
            hint = "Chave da API (sk-ant-...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("chave", ""))
        }
        raiz.addView(chave)

        scroll = ScrollView(this)
        log = TextView(this).apply { textSize = 15f; setPadding(0, dp(8), 0, dp(8)) }
        scroll.addView(log)
        raiz.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        entrada = EditText(this).apply { hint = "Digite um comando" }
        raiz.addView(entrada)

        val linha = LinearLayout(this).apply { gravity = Gravity.CENTER }
        linha.addView(botao("Enviar") {
            val t = entrada.text.toString().trim()
            if (t.isNotEmpty()) { entrada.setText(""); processar(t) }
        })
        linha.addView(botao("🎤 Falar") { ouvir() })
        raiz.addView(linha)
        raiz.addView(botao("Ativar prints (acessibilidade)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        return raiz
    }

    private fun botao(rotulo: String, acao: () -> Unit) = Button(this).apply {
        text = rotulo
        setOnClickListener { acao() }
    }

    private fun escrever(msg: String) {
        log.append(msg + "\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun ouvir() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o comando")
        try {
            startActivityForResult(i, REQ_VOZ)
        } catch (e: Exception) {
            escrever("Reconhecimento de voz indisponível neste aparelho.")
        }
    }

    @Deprecated("Simples e suficiente para este projeto")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_VOZ && res == RESULT_OK) {
            val texto = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!texto.isNullOrBlank()) processar(texto)
        }
    }

    private fun processar(pedido: String) {
        val k = chave.text.toString().trim()
        if (k.isEmpty()) { escrever("Cole a chave da API primeiro."); return }
        prefs.edit().putString("chave", k).apply()
        escrever("Você: $pedido")

        thread {
            try {
                val plano = Plano.parse(AiClient.interpretar(k, pedido))
                runOnUiThread {
                    if (plano.fala.isNotBlank()) {
                        escrever("IA: ${plano.fala}")
                        tts?.speak(plano.fala, TextToSpeech.QUEUE_FLUSH, null, "fala")
                    }
                    plano.acoes.forEach { escrever(Executor.executar(this, it)) }
                }
            } catch (e: Exception) {
                runOnUiThread { escrever("Erro: ${e.message}") }
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
