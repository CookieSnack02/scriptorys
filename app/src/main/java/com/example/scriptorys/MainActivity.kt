package com.example.scriptorys

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
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
    private lateinit var textoVazio: View
    private lateinit var dica: View

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

        adapter = ScriptAdapter(
            aoAbrir = ::escolherModo,
            aoEditar = { abrirEditor(it.id) },
            aoExcluir = ::confirmarExclusao,
        )
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = adapter

        adicaoStory.setOnClickListener { abrirEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        val scripts = repositorio.listar()
        adapter.atualizar(scripts)
        val vazio = scripts.isEmpty()
        lista.visibility = if (vazio) View.GONE else View.VISIBLE
        dica.visibility = if (vazio) View.GONE else View.VISIBLE
        textoVazio.visibility = if (vazio) View.VISIBLE else View.GONE

        // Sem scripts o botão fica no meio da tela; com scripts ele vai para o canto inferior.
        val margem = if (vazio) 0 else (24 * resources.displayMetrics.density).toInt()
        adicaoStory.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = if (vazio) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
            startToStart = if (vazio) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            marginEnd = margem
            bottomMargin = margem
        }
    }

    private fun abrirEditor(id: Long?) {
        val intent = Intent(this, EditorActivity::class.java)
        if (id != null) intent.putExtra(EditorActivity.EXTRA_ID, id)
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
    }
}
