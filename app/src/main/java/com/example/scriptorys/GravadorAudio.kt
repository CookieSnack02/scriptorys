package com.example.scriptorys

import android.Manifest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Grava áudio de um microfone específico ([dispositivo]; null = o Android escolhe) em AAC/M4A.
 * Com [saida] null, só mede o volume: é o "teste de microfone" antes de gravar.
 *
 * [inicioNanos] marca, no relógio de [System.nanoTime], o instante do primeiro som gravado,
 * para depois alinhar o áudio com o vídeo.
 */
class GravadorAudio(private val dispositivo: AudioDeviceInfo?, private val saida: File?) {

    /** Volume atual, de 0 a 1 (pico com queda suave), para o medidor na tela. */
    @Volatile
    var nivel = 0f
        private set

    @Volatile
    var inicioNanos = 0L
        private set

    /** Microfone que o Android de fato está usando (pode diferir do pedido, se ele recusar). */
    @Volatile
    var dispositivoEmUso: AudioDeviceInfo? = null
        private set

    @Volatile
    private var ativo = false

    /** Só fica true depois que o arquivo foi fechado por completo (pronto para ser lido). */
    @Volatile
    private var finalizado = false
    private var thread: Thread? = null
    private var gravouAlgo = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun iniciar() {
        val minimo = AudioRecord.getMinBufferSize(TAXA, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val tamanho = max(minimo, TAXA / 10 * 2) * 2
        // CAMCORDER é a fonte pensada para vídeo no microfone do celular; com um microfone escolhido, MIC é a
        // rota mais direta para o dispositivo externo.
        val fonte = if (dispositivo == null) MediaRecorder.AudioSource.CAMCORDER else MediaRecorder.AudioSource.MIC
        val gravador = AudioRecord(fonte, TAXA, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, tamanho)
        check(gravador.state == AudioRecord.STATE_INITIALIZED) { "Microfone indisponível" }
        gravador.setPreferredDevice(dispositivo)

        val codificador = saida?.let { criarCodificador(tamanho) }
        val muxer = saida?.let { MediaMuxer(it.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }

        gravador.startRecording()
        ativo = true
        thread = Thread({ laco(gravador, tamanho, codificador, muxer) }, "GravadorAudio").apply { start() }
    }

    /**
     * Para e fecha o arquivo, esperando a gravação terminar de escrever. O limite de espera só
     * existe para não travar o app se o microfone parar de responder; nesse caso [temAudio] é false.
     */
    fun parar() {
        ativo = false
        thread?.join(15_000)
        thread = null
    }

    /** true se algum áudio foi gravado e o arquivo já foi fechado (pode ser usado). */
    fun temAudio() = finalizado && gravouAlgo && saida?.let { it.exists() && it.length() > 0 } == true

    private fun criarCodificador(tamanho: Int): MediaCodec {
        val formato = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, TAXA, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, tamanho)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(formato, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    private fun laco(gravador: AudioRecord, tamanho: Int, codificador: MediaCodec?, muxer: MediaMuxer?) {
        val bloco = ByteArray(tamanho / 2)
        val info = MediaCodec.BufferInfo()
        val marca = AudioTimestamp()
        var amostras = 0L
        var trilha = -1
        try {
            while (ativo) {
                val lidos = gravador.read(bloco, 0, bloco.size)
                if (lidos <= 0) continue
                if (inicioNanos == 0L) {
                    dispositivoEmUso = gravador.routedDevice
                    // Momento real do primeiro som, descontando o que já estava no buffer.
                    inicioNanos = if (gravador.getTimestamp(marca, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        marca.nanoTime - marca.framePosition * 1_000_000_000L / TAXA
                    } else {
                        System.nanoTime() - (lidos / 2) * 1_000_000_000L / TAXA
                    }
                }
                medir(bloco, lidos)
                if (codificador != null && muxer != null) {
                    trilha = codificar(codificador, muxer, bloco, lidos, amostras, trilha, info, fim = false)
                }
                amostras += lidos / 2
            }
            if (codificador != null && muxer != null) {
                codificar(codificador, muxer, bloco, 0, amostras, trilha, info, fim = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gravar áudio", e)
        } finally {
            gravador.stop()
            gravador.release()
            codificador?.run { stop(); release() }
            try {
                if (gravouAlgo) muxer?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao fechar o arquivo de áudio", e)
                gravouAlgo = false
            }
            muxer?.release()
            nivel = 0f
            finalizado = true
        }
    }

    /** Entrega PCM ao codificador AAC e grava o que sair dele; devolve o índice da trilha no arquivo. */
    private fun codificar(
        codificador: MediaCodec, muxer: MediaMuxer, bloco: ByteArray, tamanho: Int, amostras: Long,
        trilhaAtual: Int, info: MediaCodec.BufferInfo, fim: Boolean,
    ): Int {
        var trilha = trilhaAtual
        var enviado = 0
        var enviouFim = false
        while (enviado < tamanho || (fim && !enviouFim)) {
            val entrada = codificador.dequeueInputBuffer(10_000)
            if (entrada >= 0) {
                val buffer = codificador.getInputBuffer(entrada)!!
                buffer.clear()
                val parte = minOf(buffer.remaining(), tamanho - enviado)
                buffer.put(bloco, enviado, parte)
                val tempoUs = (amostras + enviado / 2) * 1_000_000L / TAXA
                enviado += parte
                val ultimo = fim && enviado >= tamanho
                codificador.queueInputBuffer(entrada, 0, parte, tempoUs, if (ultimo) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                if (ultimo) enviouFim = true
            }
            trilha = esvaziar(codificador, muxer, trilha, info, aguardarFim = false)
        }
        if (fim) trilha = esvaziar(codificador, muxer, trilha, info, aguardarFim = true)
        return trilha
    }

    private fun esvaziar(codificador: MediaCodec, muxer: MediaMuxer, trilhaAtual: Int, info: MediaCodec.BufferInfo, aguardarFim: Boolean): Int {
        var trilha = trilhaAtual
        while (true) {
            val saidaIdx = codificador.dequeueOutputBuffer(info, if (aguardarFim) 10_000 else 0)
            when {
                saidaIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!aguardarFim) return trilha
                saidaIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trilha = muxer.addTrack(codificador.outputFormat)
                    muxer.start()
                }
                saidaIdx >= 0 -> {
                    val dados = codificador.getOutputBuffer(saidaIdx)!!
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0 && trilha >= 0) {
                        muxer.writeSampleData(trilha, dados, info)
                        gravouAlgo = true
                    }
                    codificador.releaseOutputBuffer(saidaIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return trilha
                }
            }
        }
    }

    private fun medir(bloco: ByteArray, tamanho: Int) {
        var pico = 0
        var i = 0
        while (i + 1 < tamanho) {
            val amostra = (bloco[i].toInt() and 0xFF) or (bloco[i + 1].toInt() shl 8)
            pico = max(pico, abs(amostra.toShort().toInt()))
            i += 2
        }
        val novo = pico / 32768f
        nivel = if (novo > nivel) novo else nivel * 0.85f + novo * 0.15f
    }

    private companion object {
        const val TAG = "GravadorAudio"
        const val TAXA = 48_000
    }
}
