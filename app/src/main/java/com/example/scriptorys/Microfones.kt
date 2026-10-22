package com.example.scriptorys

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Um microfone que pode gravar o áudio dos vídeos. */
data class Microfone(val dispositivo: AudioDeviceInfo, val nome: String, val externo: Boolean)

/**
 * Lista os microfones conectados (do celular, USB, fone com fio, Bluetooth).
 *
 * A câmera nativa de muitos celulares sempre grava pelo microfone interno, mesmo com um microfone
 * sem fio como o Hollyland conectado. Aqui o app escolhe o microfone explicitamente.
 */
object Microfones {

    fun listar(contexto: Context): List<Microfone> {
        val audio = contexto.getSystemService(AudioManager::class.java) ?: return emptyList()
        val entradas = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
        // O celular costuma ter vários microfones internos (embaixo, atrás...); basta um na lista.
        val interno = entradas.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val externos = entradas.filter { prioridade(it) > 0 }.sortedByDescending { prioridade(it) }
        return listOfNotNull(interno?.let { Microfone(it, contexto.getString(R.string.microfone_celular), false) }) +
            externos.map { Microfone(it, nome(contexto, it), true) }
    }

    /** No modo automático, usa o melhor microfone externo conectado; sem nenhum, o Android decide (interno). */
    fun automatico(lista: List<Microfone>): Microfone? = lista.firstOrNull { it.externo }

    fun nome(contexto: Context, dispositivo: AudioDeviceInfo): String {
        if (dispositivo.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) return contexto.getString(R.string.microfone_celular)
        val produto = dispositivo.productName?.toString()?.trim().orEmpty()
        val tipo = contexto.getString(
            when {
                eUsb(dispositivo) -> R.string.microfone_tipo_usb
                dispositivo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.microfone_tipo_fio
                eBluetooth(dispositivo) -> R.string.microfone_tipo_bluetooth
                else -> R.string.microfone_tipo_externo
            },
        )
        // Fones com fio costumam vir sem nome de produto (ou com o nome do próprio celular).
        return if (produto.isEmpty() || produto == Build.MODEL) tipo else "$produto ($tipo)"
    }

    /** Ordem de preferência no modo automático; 0 = não é microfone externo. */
    private fun prioridade(dispositivo: AudioDeviceInfo): Int = when {
        eUsb(dispositivo) -> 3
        dispositivo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> 2
        eBluetooth(dispositivo) -> 1
        else -> 0
    }

    private fun eUsb(dispositivo: AudioDeviceInfo) =
        dispositivo.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && dispositivo.type == AudioDeviceInfo.TYPE_USB_HEADSET)

    private fun eBluetooth(dispositivo: AudioDeviceInfo) =
        dispositivo.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dispositivo.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
}
