<p align="center">
  <img src="docs/logo-scriptorys.svg" alt="Logo do Scriptorys" width="220" style="border-radius: 18%;">
</p>

<h1 align="center">Scriptorys</h1>

<p align="center">
  Teleprompter para Android: escreva seus roteiros com formatação, leia-os rolando na velocidade da sua fala
  e grave-se olhando para a câmera com o texto na tela.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Status-Em%20Desenvolvimento-yellow" alt="Status">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Target%20SDK-37-3DDC84" alt="Target SDK">
  <img src="https://img.shields.io/badge/Gradle-9.6.0-02303A?logo=gradle&logoColor=white" alt="Gradle">
  <img src="https://img.shields.io/badge/AGP-9.4.1-4285F4" alt="Android Gradle Plugin">
  <img src="https://img.shields.io/badge/CameraX-1.6.2-4285F4" alt="CameraX">
  <img src="https://img.shields.io/badge/Material%20Design-3-757575?logo=materialdesign&logoColor=white" alt="Material Design 3">
  <img src="https://img.shields.io/badge/jsoup-1.23.2-E34F26" alt="jsoup">
</p>

---

## Sumário

- [A ideia](#a-ideia)
- [Funcionalidades](#funcionalidades)
- [Fluxo de funcionamento](#fluxo-de-funcionamento)
- [Como usar](#como-usar)
- [Controle remoto Bluetooth](#controle-remoto-bluetooth)
- [Tecnologias](#tecnologias)
- [Arquitetura e principais classes](#arquitetura-e-principais-classes)
- [Instalação](#instalação)
- [Testes](#testes)
- [Estrutura do projeto](#estrutura-do-projeto)

---

## A ideia

Quem grava vídeos, aulas, apresentações ou lives precisa falar olhando para a câmera sem decorar o texto.
O **Scriptorys** transforma o celular em um **teleprompter**: o roteiro sobe pela tela numa velocidade
compatível com a fala, e a pessoa só precisa ler.

O app foi pensado para três situações:

1. **Teleprompter de vidro** (como os da Ulanzi): o celular fica sob um vidro inclinado, que reflete o texto
   na frente da lente da câmera. Por isso o texto pode ser **espelhado** e controlado por um **controle Bluetooth**.
2. **Leitura direta na tela**: o celular fica de pé, na vertical ou na horizontal, mostrando o texto grande.
3. **Gravação com o próprio celular**: o modo **Câmera** mostra a imagem da câmera com o roteiro rolando por cima.

A velocidade é medida em **palavras por minuto (ppm)**, como na fala real. Assim, "120 ppm" significa o mesmo
ritmo em qualquer tamanho de letra ou de tela. Para quem prefere o jeito tradicional, há também o modo
**Rolagem**, com velocidade fixa de 0 a 10.

## Funcionalidades

### Tela principal
- Na primeira vez, só aparece o botão **+** (`adicaoStory`) no centro da tela.
- Depois do primeiro roteiro, aparece a **lista de scripts** e o botão **+** vai para o canto inferior.
- Gestos em cada roteiro:

  | Gesto | Ação |
  |---|---|
  | Toque | Escolher entre **Teleprompter** e **Câmera** |
  | Toque duplo | **Editar** o roteiro |
  | Segurar | **Excluir** (pede confirmação) |

### Editor de texto rico
- Título e corpo do roteiro.
- **Negrito**, *itálico*, <u>sublinhado</u>, ~~tachado~~, cor do texto, marca-texto e "limpar formatação".
- **Colar com formatação**: texto copiado do Word, Google Docs, navegadores e outros apps mantém negrito,
  itálico, sublinhado, tachado, cores, marca-texto e listas.
- Aviso ao sair com alterações não salvas.

### Teleprompter
- **Dois estados com animação entre eles:**
  - **Pausado:** painel completo com régua de velocidade, modo de velocidade, editar, opções de texto,
    configurações e os botões anterior / play / próximo.
  - **Rodando:** só uma pílula compacta com a régua de velocidade, ladeada por "voltar parágrafo" e "pausar".
- **Régua de velocidade**: arraste para os lados (com inércia), ou toque no bonequinho andando/correndo.
  Tocando na pílula "120 ppm ✏" você digita a velocidade exata.
- **Modos de velocidade**: *Palavras por minuto* (0 a 300) ou *Rolagem* (0 a 10).
- **Pular parágrafos** para frente e para trás, deslizando suavemente.
- **Opções de texto (botão TT)**: tamanho, fonte (serifada, sem serifa, monoespaçada, manuscrita) e alinhamento
  (esquerda, centro, direita, justificado).
- **Configurações (engrenagem)**: orientação (vertical/horizontal), espelhar na horizontal e/ou vertical,
  cor de fundo (preto, branco `#F9F7F0` e meio-termo `#2D2D2D`).
- **Instruções de cena**: trechos entre colchetes, como `[sorrir]` ou `[pausa]`, aparecem em laranja.
- Rolagem sempre suave: iniciar, pausar, mudar a velocidade e usar o controle aceleram e freiam aos poucos.
- Tela cheia e tela sempre ligada durante a leitura.
- As preferências ficam salvas para a próxima vez.

### Câmera
- Pede a permissão da câmera e mostra a imagem ao vivo (frontal ou traseira).
- O roteiro rola numa caixa sobre a imagem, com **posição** (topo, centro, base), **tamanho**, **cor** e
  **velocidade** ajustáveis.

## Fluxo de funcionamento

```mermaid
flowchart TD
    A[Abrir o app] --> B{Já existem scripts?}
    B -- Não --> C[Botão + no centro]
    B -- Sim --> D[Lista de scripts]
    C --> E[Editor de texto rico]
    D -- "+" --> E
    D -- toque duplo --> E
    D -- segurar --> F[Confirmar exclusão]
    E -- Salvar --> D
    D -- toque --> G{Teleprompter ou Câmera?}
    G -- Teleprompter --> H[Teleprompter pausado<br/>painel completo]
    H -- Play --> I[Teleprompter rodando<br/>pílula compacta]
    I -- Pausar --> H
    H -- "Trocar modo" --> J
    G -- Câmera --> J[Permissão da câmera]
    J --> K[Câmera com texto sobreposto]
```

Por dentro, o caminho de um roteiro é este:

1. O **editor** guarda o texto formatado como **HTML** (`Html.deTexto`), dentro de um arquivo `scripts.json`
   no armazenamento interno do app (`ScriptRepository`).
2. Ao abrir o teleprompter, o HTML volta a ser texto formatado (`Html.paraTexto`), e as instruções entre
   colchetes ganham cor.
3. A cada quadro de tela (`Choreographer`), a `RolagemAutomatica` calcula quantos pixels andar. Ela divide a
   altura do texto pelo número de palavras e multiplica pela velocidade em ppm. Depois soma o movimento do
   controle remoto, se houver.

## Como usar

1. Toque em **+** e escreva o roteiro (ou cole de outro app). Use a barra de formatação sobre o texto
   selecionado e toque em **Salvar**.
2. Toque no roteiro e escolha **Teleprompter**.
3. Ajuste a velocidade na régua, o texto no botão **TT** e a orientação, o espelhamento e o fundo na **engrenagem**.
4. Toque em **▶** (ou em qualquer ponto do texto) para começar. O painel vira a pílula compacta.
5. Para pausar, toque em **⏸**, no texto ou no botão OK do controle.
6. Para gravar com o celular, use **Trocar modo** ou escolha **Câmera** na lista.

Dica para teleprompter de vidro: em **Configurações → Espelhar**, ative **Horizontal** para o reflexo
no vidro ficar legível.

## Controle remoto Bluetooth

O app aceita controles Bluetooth como o **MOCUTE-052** que acompanha os teleprompters **Ulanzi**, em qualquer
modo do controle (setas, mídia/volume, gamepad ou mouse):

| Botão do controle | Ação no teleprompter |
|---|---|
| Cima / Volume + / Page Up / alavanca para cima / roda para cima | Voltar o texto (um toque desliza ~1,5 linha; segurando, rola continuamente) |
| Baixo / Volume − / Page Down / alavanca para baixo / roda para baixo | Avançar o texto |
| Esquerda / Faixa anterior / L1 / alavanca para a esquerda | Diminuir a velocidade |
| Direita / Próxima faixa / R1 / alavanca para a direita | Aumentar a velocidade |
| OK / Enter / Espaço / Play-Pause / botão A | Iniciar ou pausar |

Para descobrir o que um botão envia, conecte o celular ao Android Studio e filtre o **Logcat** por
`ControleRemoto`.

## Tecnologias

| Item | Uso |
|---|---|
| **Kotlin 2.2.10** | Linguagem de todo o app |
| **Android SDK** (min 24 / target 37) | Plataforma: Android 7.0 ou superior |
| **Android Gradle Plugin 9.4.1 + Gradle 9.6.0** | Build, com Kotlin embutido no AGP |
| **AndroidX AppCompat, Core KTX, Activity KTX** | Base das telas, insets e tela cheia |
| **ConstraintLayout 2.2.2** (`ConstraintSet` + `TransitionManager`) | Layouts e animação entre os estados do painel |
| **RecyclerView 1.4.0** | Lista de scripts |
| **Material Components 1.14.0** (Material 3) | Botões, diálogos, painéis inferiores, sliders, chips |
| **CameraX 1.6.2** (`camera-camera2`, `camera-lifecycle`, `camera-view`) | Prévia da câmera |
| **jsoup 1.23.2** | Leitura do HTML copiado de Word/Docs ao colar |
| **org.json** (Android) | Gravação dos scripts em JSON |
| **JUnit + AndroidX Test** | Testes instrumentados |

## Arquitetura e principais classes

O app usa **Views + XML** (sem Jetpack Compose). Cada tela é uma `Activity`, e a lógica reutilizável fica
em classes pequenas.

| Arquivo | Responsabilidade | Principais funções |
|---|---|---|
| `MainActivity` | Tela principal, lista e escolha do modo | `carregar()`, `abrirEditor()`, `escolherModo()`, `confirmarExclusao()` |
| `ScriptAdapter` | Cartões da lista e gestos (toque, toque duplo, segurar) | `GestureDetector` com `onSingleTapConfirmed`, `onDoubleTap`, `onLongPress` |
| `ScriptRepository` | Salvar, listar, buscar e excluir scripts em `scripts.json` | `listar()`, `buscar()`, `salvar()`, `excluir()` |
| `EditorActivity` | Editor de texto rico | `alternar()` (liga/desliga estilo), `aplicarCor()`, `remover()`, `cobre()` |
| `Html` (em `EditorActivity.kt`) | Conversão texto formatado ↔ HTML | `deTexto()`, `paraTexto()` |
| `ColagemFormatada` | Colar mantendo a formatação de Word/Docs | `ativar()`, `deHtml()`, `normalizar()` |
| `TeleprompterActivity` | Tela do teleprompter | `alternarPlay()`, `mudarVelocidade()`, `irParaParagrafo()`, `abrirOpcoesTexto()`, `abrirConfiguracoes()` |
| `PainelTeleprompter` | Posições do painel e animação entre os estados | `mostrar(rodando, horizontal, animar)`, `completo()`, `compacto()` |
| `ReguaVelocidade` | Régua de velocidade desenhada à mão (`View` própria) | `configurar()`, `animarPara()`, `onDraw()`, `onTouchEvent()` |
| `RolagemAutomatica` | Motor de rolagem suave (ppm, rolagem fixa e controle) | `iniciar()`, `pausar()`, `parar()`, `empurrar()`, `segurar()`, `deslizarPara()`, `doFrame()` |
| `ControleRemoto` | Tradução das teclas/eixos do controle Bluetooth | `tecla(KeyEvent)`, `movimento(MotionEvent)` |
| `ConfigTeleprompter` | Preferências do teleprompter (`SharedPreferences`) | `carregar()`, `salvar()`, `corFundo()`, `corTexto()` |
| `CameraActivity` | Câmera com texto sobreposto | `iniciarCamera()`, `aplicarAparencia()`, `aplicarPosicao()` |

## Instalação

### Requisitos
- **Android Studio** recente (com suporte ao AGP 9.4) e **JDK 17+** (o JBR que vem com o Android Studio serve).
- **Android SDK 37** instalado pelo SDK Manager.
- Um celular com **Android 7.0 ou superior**, ou um emulador.

### Pelo Android Studio
1. Clone o repositório:
   ```bash
   git clone https://github.com/CookieSnack02/scriptorys.git
   ```
2. Abra a pasta `scriptorys` no Android Studio e aguarde a sincronização do Gradle
   (ou use **File → Sync Project with Gradle Files**).
3. No celular, ative as **Opções do desenvolvedor → Depuração USB** e conecte-o ao computador.
4. Escolha o aparelho na barra superior e clique em **Run ▶**.

> Se o editor mostrar imports em vermelho logo após atualizar o projeto, sincronize o Gradle.
> Se continuar, use **File → Invalidate Caches → Invalidate and Restart**.

### Pela linha de comando
```bash
./gradlew assembleDebug
```
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
No Windows, use `gradlew.bat assembleDebug`.

## Testes

Os testes instrumentados ficam em `app/src/androidTest` e verificam a colagem com formatação
(HTML no formato do Google Docs, colagem no editor e "colar como texto simples").

```bash
./gradlew connectedDebugAndroidTest
```

> **Atenção:** o comando roda em **todos** os aparelhos conectados e **desinstala o app ao final**, apagando
> os scripts salvos. Para usar só o emulador, defina antes a variável `ANDROID_SERIAL` com o nome dele
> (por exemplo, `emulator-5554`).

## Estrutura do projeto

```
app/src/main/
├── java/com/example/scriptorys/
│   ├── MainActivity.kt           # lista de scripts
│   ├── ScriptAdapter.kt          # cartões e gestos
│   ├── ScriptRepository.kt       # armazenamento (JSON)
│   ├── EditorActivity.kt         # editor rico + conversão HTML
│   ├── ColagemFormatada.kt       # colar com formatação
│   ├── TeleprompterActivity.kt   # tela do teleprompter
│   ├── PainelTeleprompter.kt     # estados e animação do painel
│   ├── ReguaVelocidade.kt        # régua de velocidade
│   ├── RolagemAutomatica.kt      # motor de rolagem
│   ├── ControleRemoto.kt         # controle Bluetooth
│   ├── ConfigTeleprompter.kt     # preferências
│   ├── CameraActivity.kt         # modo câmera
│   └── Texto.kt                  # contagem de palavras
└── res/
    ├── layout/                   # telas, itens e painéis de opções
    ├── drawable/                 # ícones, fundos e logo
    └── values/                   # cores, textos e estilos
```
