package com.example.scriptorys

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Um script salvo. [html] guarda o texto rico (negrito, itálico, cores...) em HTML.
 * [pastaId] é null quando o script está solto, fora de qualquer pasta.
 */
data class Script(
    val id: Long,
    val titulo: String,
    val html: String,
    val atualizadoEm: Long,
    val pastaId: Long? = null,
)

/** Pasta para organizar scripts. É opcional: quem não quiser usa tudo solto. */
data class Pasta(
    val id: Long,
    val nome: String,
)

/** Persiste scripts e pastas em arquivos JSON dentro do armazenamento interno do app. */
class ScriptRepository(context: Context) {

    private val arquivo = File(context.filesDir, "scripts.json")
    private val arquivoPastas = File(context.filesDir, "pastas.json")

    fun listar(): List<Script> {
        val pastas = listarPastas().map { it.id }.toSet()
        return lerArray(arquivo)
            .map { obj ->
                Script(
                    id = obj.getLong("id"),
                    titulo = obj.optString("titulo"),
                    html = obj.optString("html"),
                    atualizadoEm = obj.optLong("atualizadoEm"),
                    // Se a pasta não existe mais, o script volta a ficar solto.
                    pastaId = obj.optLong("pastaId", -1L).takeIf { it != -1L && it in pastas },
                )
            }
            .sortedByDescending { it.atualizadoEm }
    }

    /** Scripts de uma pasta, ou os soltos quando [pastaId] é null. */
    fun listarNaPasta(pastaId: Long?): List<Script> = listar().filter { it.pastaId == pastaId }

    fun buscar(id: Long): Script? = listar().firstOrNull { it.id == id }

    /** Cria ou atualiza o script e devolve a versão salva. */
    fun salvar(id: Long?, titulo: String, html: String, pastaId: Long? = null): Script {
        val agora = System.currentTimeMillis()
        val script = Script(id ?: agora, titulo, html, agora, pastaId)
        gravarScripts(listar().filterNot { it.id == script.id } + script)
        return script
    }

    fun excluir(id: Long) {
        gravarScripts(listar().filterNot { it.id == id })
    }

    fun listarPastas(): List<Pasta> =
        lerArray(arquivoPastas)
            .map { Pasta(it.getLong("id"), it.optString("nome")) }
            .sortedBy { it.nome.lowercase() }

    fun criarPasta(nome: String): Pasta {
        val pasta = Pasta(System.currentTimeMillis(), nome.trim())
        gravarPastas(listarPastas() + pasta)
        return pasta
    }

    fun renomearPasta(id: Long, nome: String) {
        gravarPastas(listarPastas().map { if (it.id == id) it.copy(nome = nome.trim()) else it })
    }

    /** Exclui só a pasta: os scripts dela não são apagados, voltam a ficar soltos. */
    fun excluirPasta(id: Long) {
        val scripts = listar()
        gravarPastas(listarPastas().filterNot { it.id == id })
        gravarScripts(scripts.map { if (it.pastaId == id) it.copy(pastaId = null) else it })
    }

    private fun gravarScripts(scripts: List<Script>) {
        val array = JSONArray()
        scripts.forEach {
            val obj = JSONObject()
                .put("id", it.id)
                .put("titulo", it.titulo)
                .put("html", it.html)
                .put("atualizadoEm", it.atualizadoEm)
            it.pastaId?.let { pasta -> obj.put("pastaId", pasta) }
            array.put(obj)
        }
        gravar(arquivo, array)
    }

    private fun gravarPastas(pastas: List<Pasta>) {
        val array = JSONArray()
        pastas.forEach { array.put(JSONObject().put("id", it.id).put("nome", it.nome)) }
        gravar(arquivoPastas, array)
    }

    private fun lerArray(origem: File): List<JSONObject> {
        if (!origem.exists()) return emptyList()
        return try {
            val array = JSONArray(origem.readText())
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun gravar(destino: File, array: JSONArray) {
        // Grava num arquivo temporário e renomeia, para não corromper os dados se o app fechar no meio.
        val temp = File(destino.parentFile, destino.name + ".tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(destino)) {
            destino.writeText(array.toString())
            temp.delete()
        }
    }
}
