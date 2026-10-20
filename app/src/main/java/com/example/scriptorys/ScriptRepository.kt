package com.example.scriptorys

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Um script salvo. [html] guarda o texto rico (negrito, itálico, cores...) em HTML. */
data class Script(
    val id: Long,
    val titulo: String,
    val html: String,
    val atualizadoEm: Long,
)

/** Persiste os scripts num arquivo JSON dentro do armazenamento interno do app. */
class ScriptRepository(context: Context) {

    private val arquivo = File(context.filesDir, "scripts.json")

    fun listar(): List<Script> {
        if (!arquivo.exists()) return emptyList()
        return try {
            val array = JSONArray(arquivo.readText())
            (0 until array.length())
                .map { i ->
                    val obj = array.getJSONObject(i)
                    Script(
                        id = obj.getLong("id"),
                        titulo = obj.optString("titulo"),
                        html = obj.optString("html"),
                        atualizadoEm = obj.optLong("atualizadoEm"),
                    )
                }
                .sortedByDescending { it.atualizadoEm }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun buscar(id: Long): Script? = listar().firstOrNull { it.id == id }

    /** Cria ou atualiza o script e devolve a versão salva. */
    fun salvar(id: Long?, titulo: String, html: String): Script {
        val agora = System.currentTimeMillis()
        val script = Script(id ?: agora, titulo, html, agora)
        gravar(listar().filterNot { it.id == script.id } + script)
        return script
    }

    fun excluir(id: Long) {
        gravar(listar().filterNot { it.id == id })
    }

    private fun gravar(scripts: List<Script>) {
        val array = JSONArray()
        scripts.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("titulo", it.titulo)
                    .put("html", it.html)
                    .put("atualizadoEm", it.atualizadoEm)
            )
        }
        // Grava num arquivo temporário e renomeia, para não corromper os dados se o app fechar no meio.
        val temp = File(arquivo.parentFile, "scripts.json.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(arquivo)) {
            arquivo.writeText(array.toString())
            temp.delete()
        }
    }
}
