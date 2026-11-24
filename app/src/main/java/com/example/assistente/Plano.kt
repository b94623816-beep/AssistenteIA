package com.example.assistente

import org.json.JSONObject

/** Resultado interpretado: o que falar e quais ações executar. */
class Plano(val fala: String, val acoes: List<JSONObject>) {
    companion object {
        fun parse(bruto: String): Plano {
            val ini = bruto.indexOf('{')
            val fim = bruto.lastIndexOf('}')
            if (ini < 0 || fim <= ini) return Plano(bruto.trim(), emptyList())
            val json = JSONObject(bruto.substring(ini, fim + 1))
            val arr = json.optJSONArray("acoes")
            val lista = mutableListOf<JSONObject>()
            if (arr != null) for (i in 0 until arr.length()) lista.add(arr.getJSONObject(i))
            return Plano(json.optString("fala", ""), lista)
        }
    }
}
