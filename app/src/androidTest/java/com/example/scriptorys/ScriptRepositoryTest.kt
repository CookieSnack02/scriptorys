package com.example.scriptorys

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScriptRepositoryTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repositorio: ScriptRepository

    @Before
    fun limpar() {
        apagarArquivos()
        repositorio = ScriptRepository(contexto)
    }

    @After
    fun apagarArquivos() {
        listOf("scripts.json", "pastas.json").forEach { File(contexto.filesDir, it).delete() }
    }

    @Test
    fun scriptsSemPastaContinuamSoltos() {
        repositorio.salvar(null, "Solto", "<p>texto</p>")
        assertEquals(listOf("Solto"), repositorio.listarNaPasta(null).map { it.titulo })
        assertNull(repositorio.listar().single().pastaId)
    }

    @Test
    fun scriptDentroDaPasta() {
        val pasta = repositorio.criarPasta("  Aulas  ")
        assertEquals("Aulas", pasta.nome)
        repositorio.salvar(null, "Aula 1", "<p>a</p>", pasta.id)
        Thread.sleep(2)
        repositorio.salvar(null, "Solto", "<p>b</p>")

        assertEquals(listOf("Aula 1"), repositorio.listarNaPasta(pasta.id).map { it.titulo })
        assertEquals(listOf("Solto"), repositorio.listarNaPasta(null).map { it.titulo })
    }

    @Test
    fun editarMantemOuTrocaAPasta() {
        val pasta = repositorio.criarPasta("Vídeos")
        val script = repositorio.salvar(null, "Roteiro", "<p>a</p>", pasta.id)
        repositorio.salvar(script.id, "Roteiro editado", "<p>b</p>", pasta.id)
        assertEquals("Roteiro editado", repositorio.listarNaPasta(pasta.id).single().titulo)

        repositorio.salvar(script.id, "Roteiro editado", "<p>b</p>", null)
        assertEquals(0, repositorio.listarNaPasta(pasta.id).size)
        assertEquals(1, repositorio.listarNaPasta(null).size)
    }

    @Test
    fun renomearPasta() {
        val pasta = repositorio.criarPasta("Rascunho")
        repositorio.renomearPasta(pasta.id, "Final")
        assertEquals(listOf("Final"), repositorio.listarPastas().map { it.nome })
    }

    @Test
    fun excluirPastaNaoApagaOsScripts() {
        val pasta = repositorio.criarPasta("Temporária")
        repositorio.salvar(null, "Dentro", "<p>a</p>", pasta.id)

        repositorio.excluirPasta(pasta.id)

        assertEquals(0, repositorio.listarPastas().size)
        val script = repositorio.listar().single()
        assertEquals("Dentro", script.titulo)
        assertNull(script.pastaId)
    }

    @Test
    fun pastasEmOrdemAlfabetica() {
        repositorio.criarPasta("zebra")
        Thread.sleep(2)
        repositorio.criarPasta("Abelha")
        assertEquals(listOf("Abelha", "zebra"), repositorio.listarPastas().map { it.nome })
    }
}
