package com.example.scriptorys

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var repositorio: ScriptRepository
    private lateinit var adapter: ScriptAdapter
    private lateinit var lista: RecyclerView
    private lateinit var adicaoStory: Button
    private lateinit var textoVazio: TextView
    private lateinit var dica: TextView
    private lateinit var barraPasta: View
    private lateinit var botaoNovaPasta: View

    /** Pasta aberta no momento; null é a lista principal (pastas + scripts soltos). */
    private var pastaAtual: Long? = null
    private lateinit var sairDaPasta: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repositorio = ScriptRepository(this)
        lista = findViewById(R.id.listaScripts)
        adicaoStory = findViewById(R.id.adicaoStory)
        textoVazio = findViewById(R.id.textoVazio)
        dica = findViewById(R.id.dica)
        barraPasta = findViewById(R.id.barraPasta)
        botaoNovaPasta = findViewById(R.id.botaoNovaPasta)

        adapter = ScriptAdapter(
            aoAbrir = ::escolherModo,
            aoEditar = { abrirEditor(it.id) },
            aoExcluir = ::confirmarExclusao,
            aoAbrirPasta = { abrirPasta(it.id) },
            aoSegurarPasta = ::opcoesDaPasta,
        )
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adapter

        adicaoStory.setOnClickListener { abrirEditor(null) }
        botaoNovaPasta.setOnClickListener {
            DialogosPasta.pedirNome(this, R.string.nova_pasta) { nome ->
                repositorio.criarPasta(nome)
                carregar()
            }
        }
        findViewById<View>(R.id.botaoSairPasta).setOnClickListener { abrirPasta(null) }
        // Dentro de uma pasta, "voltar" retorna à lista principal em vez de fechar o app.
        sairDaPasta = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = abrirPasta(null)
        }
        onBackPressedDispatcher.addCallback(this, sairDaPasta)

        pastaAtual = savedInstanceState?.getLong(ESTADO_PASTA, -1L)?.takeIf { it != -1L }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(ESTADO_PASTA, pastaAtual ?: -1L)
    }

    private fun abrirPasta(id: Long?) {
        pastaAtual = id
        carregar()
        lista.scrollToPosition(0)
    }

    private fun opcoesDaPasta(pasta: Pasta) {
        MaterialAlertDialogBuilder(this)
            .setTitle(pasta.nome)
            .setItems(arrayOf(getString(R.string.renomear_pasta), getString(R.string.excluir_pasta))) { _, opcao ->
                if (opcao == 0) {
                    DialogosPasta.pedirNome(this, R.string.renomear_pasta, pasta.nome, R.string.renomear) { nome ->
                        repositorio.renomearPasta(pasta.id, nome)
                        carregar()
                    }
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.excluir_pasta)
                        .setMessage(getString(R.string.excluir_pasta_mensagem, pasta.nome))
                        .setNegativeButton(R.string.cancelar, null)
                        .setPositiveButton(R.string.excluir) { _, _ ->
                            repositorio.excluirPasta(pasta.id)
                            carregar()
                        }
                        .show()
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        val pastas = repositorio.listarPastas()
        // A pasta pode ter sido excluída enquanto estava aberta.
        val pasta = pastaAtual?.let { id -> pastas.firstOrNull { it.id == id } }
        if (pasta == null) pastaAtual = null

        val scripts = repositorio.listar()
        val itens = if (pasta == null) {
            pastas.map { p -> ItemLista.DePasta(p, scripts.count { it.pastaId == p.id }) } +
                scripts.filter { it.pastaId == null }.map { ItemLista.DeScript(it) }
        } else {
            scripts.filter { it.pastaId == pasta.id }.map { ItemLista.DeScript(it) }
        }
        adapter.atualizar(itens)

        sairDaPasta.isEnabled = pasta != null
        barraPasta.visibility = if (pasta != null) View.VISIBLE else View.GONE
        botaoNovaPasta.visibility = if (pasta == null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tituloPasta).text = pasta?.nome

        val vazio = itens.isEmpty()
        // Sem nada na tela inicial, o botão fica sozinho no meio; nos outros casos vai para o canto.
        val centralizado = vazio && pasta == null
        lista.visibility = if (vazio) View.GONE else View.VISIBLE
        dica.visibility = if (vazio) View.GONE else View.VISIBLE
        dica.text = if (pastas.isNotEmpty() && pasta == null) {
            getString(R.string.dica_lista) + "\n" + getString(R.string.dica_pasta)
        } else {
            getString(R.string.dica_lista)
        }
        textoVazio.visibility = if (vazio) View.VISIBLE else View.GONE
        textoVazio.setText(if (pasta != null) R.string.pasta_vazia else R.string.lista_vazia)
        textoVazio.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (centralizado) {
                topToBottom = R.id.adicaoStory
                topToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            } else {
                topToBottom = ConstraintLayout.LayoutParams.UNSET
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        }

        val margem = if (centralizado) 0 else (24 * resources.displayMetrics.density).toInt()
        adicaoStory.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = if (centralizado) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
            startToStart = if (centralizado) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            marginEnd = margem
            bottomMargin = margem
        }
    }

    private fun abrirEditor(id: Long?) {
        val intent = Intent(this, EditorActivity::class.java)
        if (id != null) {
            intent.putExtra(EditorActivity.EXTRA_ID, id)
        } else {
            // Script novo criado dentro de uma pasta já nasce nela.
            pastaAtual?.let { intent.putExtra(EditorActivity.EXTRA_PASTA, it) }
        }
        startActivity(intent)
    }

    private fun escolherModo(script: Script) {
        val conteudo = LayoutInflater.from(this).inflate(R.layout.tipoprompt, null)
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(script.titulo.ifBlank { getString(R.string.sem_titulo) })
            .setMessage(R.string.escolher_modo)
            .setView(conteudo)
            .setNegativeButton(R.string.cancelar, null)
            .show()
        conteudo.findViewById<Button>(R.id.teleprompter).setOnClickListener {
            dialogo.dismiss()
            startActivity(Intent(this, TeleprompterActivity::class.java).putExtra(EXTRA_SCRIPT_ID, script.id))
        }
        conteudo.findViewById<Button>(R.id.camera).setOnClickListener {
            dialogo.dismiss()
            startActivity(Intent(this, CameraActivity::class.java).putExtra(EXTRA_SCRIPT_ID, script.id))
        }
    }

    private fun confirmarExclusao(script: Script) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.excluir_titulo)
            .setMessage(getString(R.string.excluir_mensagem, script.titulo.ifBlank { getString(R.string.sem_titulo) }))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.excluir) { _, _ ->
                repositorio.excluir(script.id)
                carregar()
            }
            .show()
    }

    companion object {
        const val EXTRA_SCRIPT_ID = "script_id"
        private const val ESTADO_PASTA = "pasta"
    }
}
