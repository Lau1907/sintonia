# 🎵 Sintonía — Control Multimedia Inteligente para el Ecosistema Digital

---

## 👩‍💻 Información del Proyecto

| Campo | Detalle |
|---|---|
| **Nombre del proyecto** | Sintonía |
| **Estudiantes** | Medrano Hernández Vanesa Monserrat · Tapia Cid Laura Berenice |
| **Matrícula** | 1222100447 · 1222100476 |
| **Grupo** | GIDS6093 |
| **Materia** | Desarrollo para Dispositivos Inteligentes |
| **Docente** | Rodríguez García Anastacio |
| **Institución** | Universidad Tecnológica del Norte de Guanajuato |
| **Periodo** | Mayo – Agosto 2026 |

---

## 🎯 Objetivo

Desarrollar un sistema de control multimedia multiplataforma que integre **Smartwatch**, **Smartphone** y **Smart TV**, combinando fuentes de contenido gratuito y legal (Jamendo y Radio Garden) con comunicación en tiempo real entre los tres dispositivos a través de Firebase Realtime Database.

---

## 📱 Descripción de Funcionalidades

### Smartphone (Hub Central)
- Selección de fuente de reproducción: Jamendo, Radio Garden
- Búsqueda y reproducción de música gratuita con licencia Creative Commons (Jamendo API)
- Reproductor de audio con controles play/pausa
- Sincronización del estado de reproducción hacia Firebase en tiempo real
- Recepción de comandos del Smartwatch

### Smartwatch (Wear OS)
- Visualización de la canción en reproducción (título y artista)
- Control de play/pausa directamente desde la muñeca
- Sincronización en tiempo real vía Firebase Realtime Database

### Android TV (Dashboard)
- Dashboard visual que muestra la portada del álbum, título y artista
- Indicador de estado (reproduciendo / pausado)
- Actualización en tiempo real sin interacción del usuario
- Controlado desde el smartphone vía Firebase

---

## 🛠️ Tecnologías Utilizadas

| Tecnología | Uso |
|---|---|
| **Kotlin** | Lenguaje principal |
| **Jetpack Compose** | UI declarativa en los 3 módulos |
| **Material Design 3** | Sistema de diseño |
| **Wear OS SDK** | Módulo smartwatch |
| **Android TV** | Módulo Smart TV |
| **Firebase Realtime Database** | Comunicación en tiempo real |
| **Jamendo API** | Música gratuita bajo Creative Commons |
| **ExoPlayer (Media3)** | Reproducción de audio |
| **Retrofit 2 + OkHttp** | Consumo de APIs REST |
| **Coil** | Carga de imágenes |
| **MVVM + Repository Pattern** | Arquitectura de software |
| **Android Studio** | IDE de desarrollo |
| **Git + GitHub** | Control de versiones |

---

## 🗂️ Estructura del Repositorio

```
sintonia/
├── app/                    # Módulo Smartphone (hub central)
├── wear/                   # Módulo Smartwatch (Wear OS)
├── tv/                     # Módulo Android TV
├── apk/
│   └── sintonia.apk        # APK generado de la app principal
├── evidencias/
│   ├── pantalla_principal.png
│   ├── navegacion.png
│   ├── jamendo_busqueda.png
│   ├── wear_os.png
│   └── android_tv.png
└── README.md
```

---

## ▶️ Instrucciones para Ejecutar el Proyecto

### Requisitos previos
- Android Studio Hedgehog o superior
- JDK 11
- Cuenta en [Firebase Console](https://console.firebase.google.com)
- Cuenta en [Jamendo Developer Portal](https://devportal.jamendo.com) (gratuita)
- Emulador o dispositivo físico:
  - Android 8.0+ (API 26) para el smartphone
  - Wear OS para el smartwatch
  - Android TV para la TV

### Pasos

1. **Clona el repositorio**
   ```bash
   git clone https://github.com/TU_USUARIO/sintonia.git
   cd sintonia
   ```

2. **Configura Firebase**
   - Crea un proyecto en [Firebase Console](https://console.firebase.google.com)
   - Agrega las apps: `com.sintonia.app`, `com.sintonia.wear`, `com.sintonia.tv`
   - Descarga cada `google-services.json` y colócalo en la carpeta raíz de cada módulo
   - Activa **Realtime Database** en modo test

3. **Configura Jamendo**
   - Regístrate en [devportal.jamendo.com](https://devportal.jamendo.com)
   - Crea una aplicación y copia tu **Client ID**
   - Pégalo en `app/src/main/java/com/sintonia/app/data/remote/JamendoApi.kt`
     ```kotlin
     @Query("client_id") clientId: String = "TU_CLIENT_ID_AQUI"
     ```

4. **Abre en Android Studio**
   - File → Open → selecciona la carpeta del proyecto
   - Espera a que Gradle sincronice

5. **Ejecuta cada módulo**
   - Smartphone: selecciona `:app` y corre en emulador de teléfono
   - Smartwatch: selecciona `:wear` y corre en emulador Wear OS
   - TV: selecciona `:tv` y corre en emulador Android TV

---

## 📸 Capturas de Pantalla

### Smartphone — Pantalla Principal Jamendo
<img width="317" height="705" alt="image" src="https://github.com/user-attachments/assets/de25453b-4bcc-4f6e-b80b-124a55d0284e" />

### Smartphone — Búsqueda en Jamendo
<img width="317" height="715" alt="image" src="https://github.com/user-attachments/assets/e8e98802-6869-4e3d-9a5e-8a3dcb91c6a8" />

### Smartwatch — Control de Reproducción
<img width="710" height="701" alt="image" src="https://github.com/user-attachments/assets/2138a8b1-3eb7-4940-bc90-49006b28f6db" />

### Smartwatch — Pantalla de notificación
<img width="576" height="581" alt="image" src="https://github.com/user-attachments/assets/fba909c4-385c-4906-b9e4-a79470666443" />

### Smartwatch — Pantalla de volumen
<img width="533" height="497" alt="image" src="https://github.com/user-attachments/assets/6a15e339-26e7-4870-9b2b-1a9d5271e63a" />

---

## 🔗 APIs Utilizadas

- **Jamendo API** — https://api.jamendo.com/v3.0/ · Música gratuita bajo licencia Creative Commons
- **Firebase Realtime Database** — Comunicación en tiempo real entre dispositivos

---

## 📄 Licencia

Proyecto académico desarrollado para la materia **Desarrollo para Dispositivos Inteligentes** — UTNG, periodo Mayo–Agosto 2026.
