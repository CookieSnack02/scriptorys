package com.example.scriptorys

object Texto {
    private val palavra = Regex("\\S+")

    fun contarPalavras(texto: CharSequence): Int = palavra.findAll(texto).count()
}
