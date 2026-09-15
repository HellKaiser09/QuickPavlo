# Contexto Maestro y Master Plan: Proyecto de Transferencia P2P (Kotlin)

**Instrucción de Sistema para el Agente:** Las decisiones arquitectónicas, tecnológicas y logísticas descritas en este documento son **inamovibles**. No hay debate sobre el stack, los emuladores o la adición de características fuera de este MVP. Cíñete estrictamente a estos parámetros.
1. Definición del Proyecto y Acta de Constitución
   Objetivo General: Desarrollar una aplicación móvil Android nativa que establezca redes locales punto a punto (P2P) seguras mediante el escaneo de un código QR, permitiendo el chat de texto offline y garantizando la transferencia de archivos reanudable y tolerante a fallos de red.

Problema a resolver: Transferencia de archivos (imágenes, textos, videos ligeros) en entornos sin conexión a internet (datos móviles/Wi-Fi externo) y mitigación de interrupciones de conexión.

Mecanismo Técnico: El código QR no transfiere archivos; actúa como un "token de apretón de manos" para intercambiar credenciales y conectar ambos dispositivos vía red local P2P.

Restricción Física (Bloqueante): Desarrollo y QA estrictamente en dispositivos Android físicos. Los emuladores estándar no soportan Wi-Fi Direct para validar Google Nearby Connections ni hardware de cámara.

Logística:

Equipo de 6 desarrolladores nivel junior (uno asume rol dual Tech Lead/Dev).

Metodología Híbrida (Scrumban) con sprints rígidos de 1 semana.

1. Arquitectura y Stack Tecnológico
   Patrón Arquitectónico: Monolito Cliente (App Móvil Autónoma) estructurado en Clean Architecture + MVVM (Capas de Presentación, Dominio y Datos).

Lenguaje: Kotlin (Uso intensivo de Coroutines y StateFlows).

Interfaz (UI): Jetpack Compose (evita conflictos de merge en XML).

Motor P2P: Google Nearby Connections API (Estrategia P2P_POINT_TO_POINT).

Procesamiento QR: Google ML Kit (Vision API - 100% offline).

Base de Datos Local: Room (SQLite) - Almacena el estado de los chunks recibidos y el chat en vivo.

Inyección de Dependencias: Hilt - Vital para desacoplar el trabajo (ej. la UI usa mocks mientras se programa la red).

1. Seguridad y Riesgos (Enfoque Zero-Trust)
   Intercepción QR / Handshake: Requiere validación de PIN cruzado numérico en pantalla usando la API de Nearby Connections antes de autorizar el canal de datos.

Cifrado: Nearby Connections maneja cifrado simétrico en tránsito (estrictamente prohibido desactivarlo).

Zero Leakage (Almacenamiento Físico): Volatilidad de sesión. Room guarda el progreso en texto plano para el MVP, pero el historial se trunca/elimina al cerrar la conexión. Riesgo aceptado, documentado contra extracción física.

Aislamiento de Red: Entorno air-gapped. Prohibido incluir analíticas externas (ej. Firebase) o salidas a internet. Solo permisos locales justificados (CAMERA, NEARBY_WIFI_DEVICES).

1. Logística de Equipo y Entorno (GitHub)
   Estrategia Git: GitHub Flow modificado.

Ramas main y develop bloqueadas.

Desarrollo exclusivo en ramas feature/.

Pull Request (PR) requiere mínimo 1 revisión/Approve obligatoria de otro miembro (ningún dev aprueba su propio código).

Ningún PR pasa a "Done" si no se probó en 2 celulares físicos.

Infraestructura Inicial (Backlog Técnico):

T01: Inicializar Repo + .gitignore.

T02: Proyecto base Jetpack Compose.

T03: Configuración build.gradle.kts (dependencias).

T04: Setup de Hilt (@HiltAndroidApp).

T05: CI básico (GitHub Actions para ./gradlew build).

1. Diagrama de Arquitectura (Mermaid)
   Fragmento de código
   graph TD
   classDef ui fill:#e3f2fd,stroke:#1e88e5,stroke-width:2px,color:#000
   classDef domain fill:#f3e5f5,stroke:#8e24aa,stroke-width:2px,color:#000
   classDef data fill:#e8f5e9,stroke:#43a047,stroke-width:2px,color:#000
   classDef external fill:#fff3e0,stroke:#fb8c00,stroke-width:2px,stroke-dasharray: 5 5,color:#000

   subgraph Presentacion [Capa de Presentación / UI]
   direction TB
   UI_Scan("Pantalla Escáner (Compose)") ::: ui
   UI_Chat("Pantalla Chat y Transferencia (Compose)") ::: ui
   VM("ViewModels (Gestores de Estado)") ::: ui
   end

   subgraph Dominio [Capa de Dominio / Reglas de Negocio]
   direction TB
   UC("Casos de Uso (TransferUseCase, ChatUseCase)") ::: domain
   Models("Modelos Puros (Archivo, Chunk, Mensaje)") ::: domain
   end

   subgraph Datos [Capa de Datos / Infraestructura]
   direction TB
   Repos("Repositorios (FileRepo, NetworkRepo)") ::: data
   DB[("BD Local (Room / SQLite)")] ::: data
   P2P("Controlador P2P (Nearby Connections)") ::: data
   Cam("Controlador de Cámara (CameraX)") ::: data
   end

   MLKit("Google ML Kit (Vision API)") ::: external
   Receptor("Dispositivo Receptor (El otro teléfono)") ::: external

   UI_Scan -->|Eventos de usuario| VM
   UI_Chat -->|Eventos de usuario| VM
   VM -->|Observa estado| UI_Scan
   VM -->|Observa estado| UI_Chat
   VM -->|Ejecuta lógica| UC

   UC -.->|Usa| Models
   UC -->|Pide/Envía información| Repos

   Repos <-->|Lee/Escribe progreso| DB
   Repos <-->|Gestiona transmisión| P2P
   Repos -->|Inicia captura| Cam

   Cam -->|Envía frames offline| MLKit
   MLKit -->|Devuelve QR decodificado| Repos
   P2P <-->|Canal TCP Local Cifrado| Receptor
2. Estructura de Carpetas (Clean Architecture)
   Plaintext
   app/src/main/java/com/tuorganizacion/p2ptransfer/
   ├── di/                     # Módulos de Hilt (Provisión de BD, Red P2P, Escáner)
   ├── data/                   # Capa de Datos
   │   ├── local/              # Room (AppDatabase, DAOs)
   │   ├── network/            # Implementación Nearby Connections
   │   └── repository/         # Implementación de repositorios
   ├── domain/                 # Capa de Dominio
   │   ├── model/              # Clases puras
   │   ├── repository/         # Interfaces
   │   └── usecase/            # Casos de uso (Lógica de chunking)
   └── ui/                     # Capa de Presentación
   ├── theme/              # Material 3
   ├── feature/            # scanner, transfer, chat
   └── MainActivity.kt     # NavHost
3. Épicas e Historias de Usuario Core (MVP)
   Épica 1: Protocolo de Reconocimiento (Handshake P2P)

HU1: Como anfitrión, quiero generar un QR con credenciales de sesión efímeras para que un invitado se una sin configuración manual.

HU2: Como invitado, quiero leer el QR usando ML Kit para emparejarme automáticamente con el anfitrión.

Épica 2: Canal de Comunicación Táctica (Chat)

HU3: Como sistema de seguridad, quiero exigir la validación cruzada de un PIN numérico en ambas pantallas para autorizar el canal P2P.

HU4: Como usuario conectado, quiero enviar y recibir mensajes de texto en una interfaz limpia para comunicarme offline.

Épica 3: Motor de Transferencia Blindado

HU5 (Rendimiento): Como motor de datos, quiero fragmentar archivos salientes en chunks asíncronos (Dispatchers.IO) para no saturar la RAM ni bloquear el Main Thread (ANR) en transferencias pesadas.

HU6 (Tolerancia a caídas): Como sistema de resiliencia, quiero registrar el ID de cada chunk validado en Room y detectar caídas de red en <5s, para reanudar descargas interrumpidas exactamente desde el fallo al reconectar.

1. Roadmap de Ejecución (4 Semanas)
   Sprint 1 (Semana 1): Andamiaje y Handshake P2P. Foco: Setup (Hilt, Room vacío), UI de permisos, generación/escaneo de QR y prueba de conexión P2P_POINT_TO_POINT.

Sprint 2 (Semana 2): Canal de Comunicación y Seguridad. Foco: UI del chat en Compose, validación del PIN cruzado, envío de texto y limpieza de BD al desconectar.

Sprint 3 (Semana 3): Motor de Transferencia Base. Foco: Lectura de archivos, chunking en Dispatchers.IO, envío secuencial (Flujo Feliz) y UI de barra de progreso.

Sprint 4 (Semana 4): Tolerancia a Fallos y Entrega. Foco: Persistencia de índices en Room, detección de pausa, lógica de reanudación automática y QA final cruzado.