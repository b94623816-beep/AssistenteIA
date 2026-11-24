package com.example.assistente

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONObject

/** Executa no celular cada ação pedida pela IA. Devolve um texto de status. */
object Executor {

    fun executar(ctx: Context, a: JSONObject): String = try {
        when (val tipo = a.optString("tipo")) {
            "alarme" -> alarme(ctx, a)
            "timer" -> timer(ctx, a)
            "sms" -> sms(ctx, a)
            "whatsapp" -> whatsapp(ctx, a)
            "ligar" -> ligar(ctx, a)
            "abrir_app" -> abrirApp(ctx, a.optString("app"))
            "print" -> print(ctx)
            "lanterna" -> lanterna(ctx, a.optBoolean("ligar", true))
            "configuracoes" -> configuracoes(ctx, a.optString("tela"))
            "pesquisar" -> pesquisar(ctx, a.optString("busca"))
            else -> "Ação desconhecida: $tipo"
        }
    } catch (e: Exception) {
        "Falha ao executar: ${e.message}"
    }

    private fun iniciar(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun alarme(ctx: Context, a: JSONObject): String {
        val h = a.getInt("hora")
        val m = a.optInt("minuto", 0)
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("texto", "Alarme"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val dias = a.optJSONArray("dias")
        if (dias != null && dias.length() > 0) {
            val lista = ArrayList<Int>()
            for (k in 0 until dias.length()) lista.add(dias.getInt(k))
            i.putExtra(AlarmClock.EXTRA_DAYS, lista)
        }
        iniciar(ctx, i)
        return "⏰ Alarme criado para %02d:%02d".format(h, m)
    }

    private fun timer(ctx: Context, a: JSONObject): String {
        val s = a.getInt("segundos")
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("texto", "Timer"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        iniciar(ctx, i)
        return "⏳ Timer de $s segundos iniciado"
    }

    private fun sms(ctx: Context, a: JSONObject): String {
        val num = a.optString("numero", "")
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
            .putExtra("sms_body", a.optString("texto", ""))
        iniciar(ctx, i)
        return "💬 SMS pronto para enviar (toque em enviar)"
    }

    private fun whatsapp(ctx: Context, a: JSONObject): String {
        val num = a.optString("numero", "").filter { it.isDigit() }
        val txt = Uri.encode(a.optString("texto", ""))
        val url = if (num.isNotEmpty()) "https://wa.me/$num?text=$txt" else "https://wa.me/?text=$txt"
        iniciar(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return "🟢 WhatsApp aberto com a mensagem (toque em enviar)"
    }

    private fun ligar(ctx: Context, a: JSONObject): String {
        val num = a.getString("numero")
        iniciar(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
        return "📞 Discador aberto com $num"
    }

    private fun abrirApp(ctx: Context, nome: String): String {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val achado = pm.queryIntentActivities(launcher, 0).firstOrNull {
            it.loadLabel(pm).toString().contains(nome, ignoreCase = true)
        } ?: return "App \"$nome\" não encontrado"
        val i = pm.getLaunchIntentForPackage(achado.activityInfo.packageName)
            ?: return "Não consegui abrir \"$nome\""
        iniciar(ctx, i)
        return "📱 Abrindo ${achado.loadLabel(pm)}"
    }

    private fun print(ctx: Context): String {
        val s = ScreenshotService.instance
            ?: return "⚠️ Ative o serviço de acessibilidade do Assistente para tirar prints (botão no app)."
        s.tirarPrint()
        return "📸 Print tirado (veja na galeria)"
    }

    private fun lanterna(ctx: Context, ligar: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Este aparelho não tem lanterna"
        cm.setTorchMode(id, ligar)
        return if (ligar) "🔦 Lanterna ligada" else "🔦 Lanterna desligada"
    }

    private fun configuracoes(ctx: Context, tela: String): String {
        val acao = when (tela) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "som" -> Settings.ACTION_SOUND_SETTINGS
            "tela" -> Settings.ACTION_DISPLAY_SETTINGS
            "bateria" -> Intent.ACTION_POWER_USAGE_SUMMARY
            else -> Settings.ACTION_SETTINGS
        }
        iniciar(ctx, Intent(acao))
        return "⚙️ Abrindo configurações ($tela)"
    }

    private fun pesquisar(ctx: Context, busca: String): String {
        iniciar(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", busca))
        return "🔎 Pesquisando: $busca"
    }
}
