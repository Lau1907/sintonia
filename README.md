# 🎵 Sintonía — Control Multimedia Inteligente para el Ecosistema Digital

## 👩‍💻 Información del Proyecto

| Campo | Detalle |
| :--- | :--- |
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

Desarrollar un sistema de control multimedia multiplataforma que integre Smartwatch, Smartphone y Smart TV, combinando fuentes de contenido gratuito y legal (Jamendo y Radio Garden) con comunicación en tiempo real entre los tres dispositivos a través de Firebase Realtime Database.

---

## 📱 Descripción de Funcionalidades

### Smartphone (Hub Central)
*   **Selección de fuente:** Jamendo, Radio Garden y Spotify.
*   **Búsqueda y reproducción:** Acceso a música gratuita con licencia Creative Commons (Jamendo API).
*   **Pantalla de Favoritos:** Función para guardar pistas y acceder rápidamente a ellas, superando las limitaciones de descarga directa de servicios como Spotify.
*   **Streaming Flexible:** Opción para alternar instantáneamente la salida de audio/video entre el Smartphone y la Smart TV.
*   **Gestión:** Reproductor con controles y sincronización total del estado hacia Firebase.
*   **Control Remoto:** Recepción de comandos enviados desde el Smartwatch.

### Smartwatch (Wear OS)
*   Visualización de la canción en reproducción (título y artista).
*   Control de play/pausa directamente desde la muñeca.
*   Sincronización en tiempo real vía Firebase Realtime Database.

### Android TV (Dashboard)
*   **Dashboard Visual:** Muestra portada del álbum, título y artista.
*   **Cola de Reproducción:** Visualización en tiempo real de la lista de próximas canciones, sincronizada automáticamente con el Smartphone.
*   **Indicador de estado:** Estado (reproduciendo / pausado) y visualizador de ondas.
*   **Control Remoto:** Actualización en tiempo real controlada desde el smartphone vía Firebase.

---

## 🏗️ Diagrama de Arquitectura

```mermaid
graph TD
    A[Smartphone - Hub Central] -->|Firebase Realtime Database| B[Android TV]
    A -->|Wearable Data Layer| C[Wear OS]
    A -->|Consume APIs| D[Jamendo, Radio Garden, Spotify, YouTube]
```
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
   git clone https://github.com/Lau1907/sintonia.git
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

### Smartphone — Pantalla Principal
<img width="1080" height="2400" alt="WhatsApp Image 2026-08-12 at 12 15 47" src="https://github.com/user-attachments/assets/931232a9-cf12-48eb-a699-53b8ab4ea351" />

### Smartphone — Pantalla Principal y busqueda en Jamendo
<img width="720" height="1600" alt="WhatsApp Image 2026-08-12 at 12 15 46" src="https://github.com/user-attachments/assets/9a4c26ad-6fab-4b27-b710-ee8c37fd9782" />

### Smartphone — Pantalla de Spotify
<img width="720" height="1600" alt="WhatsApp Image 2026-08-12 at 12 21 14" src="https://github.com/user-attachments/assets/e19fda69-060d-445e-9f37-edd2ed8a5850" />

### Smartphone — Pantalla de Radio Garden
<img width="720" height="1600" alt="WhatsApp Image 2026-08-12 at 12 24 01" src="https://github.com/user-attachments/assets/f9189a8e-adba-4bb8-8c09-dda22bf54434" />

### Smartphone — Pantalla de YouTube
<img width="720" height="1600" alt="WhatsApp Image 2026-08-12 at 12 15 4" src="https://github.com/user-attachments/assets/012c2e51-8fc3-429e-9994-3b0f44a7f662" />

### Smartphone — Pantalla de Descargas
<img width="1080" height="2400" alt="WhatsApp Image 2026-08-12 at 12 15 47" src="https://github.com/user-attachments/assets/e44c3766-aa63-4371-98fe-bb4ad8705dd1" />

### Smartphone — Pantalla de Favoritos
<img width="1080" height="2400" alt="WhatsApp Image 2026-08-12 at 12 15 47" src="https://github.com/user-attachments/assets/21882f5a-2a59-4d68-a61f-4c793db6fa19" />

### Smartphone — Pantalla de Cola de Reproducción
<img width="1080" height="2400" alt="WhatsApp Image 2026-08-12 at 12 15" src="https://github.com/user-attachments/assets/860b5f4f-37cf-4e2a-8d2a-838ca4b19224" />


### Smartwatch — Control de Reproducción
<img width="710" height="701" alt="image" src="https://github.com/user-attachments/assets/2138a8b1-3eb7-4940-bc90-49006b28f6db" />

### Smartwatch — Pantalla de notificación
<img width="576" height="581" alt="image" src="https://github.com/user-attachments/assets/fba909c4-385c-4906-b9e4-a79470666443" />

### Smartwatch — Pantalla de volumen
<img width="533" height="497" alt="image" src="https://github.com/user-attachments/assets/6a15e339-26e7-4870-9b2b-1a9d5271e63a" />

### Android TV — Pantalla de Spotify
<img width="918" height="531" alt="WhatsApp Image 2026-08-12 at 12 05 28" src="https://github.com/user-attachments/assets/b18d2cfb-3c8a-4686-b6ee-bfb0b3ba5ad2" />

### Android TV — Pantalla de Jamendo
<img width="913" height="531" alt="WhatsApp Image 2026-08-12 at 12 06 33" src="https://github.com/user-attachments/assets/fb1880cb-a9d6-40c2-b870-6e5595621fd5" />

### Android TV — Pantalla de Radio Garden
<img width="912" height="523" alt="WhatsApp Image 2026-08-12 at 12 07 37" src="https://github.com/user-attachments/assets/366b7c4b-fa46-4e01-9607-ee9ca0fa1bfb" />

### Android TV — Pantalla de YouTube
<img width="912" height="528" alt="WhatsApp Image 2026-08-12 at 12 14 44" src="https://github.com/user-attachments/assets/bb38766f-bc07-40db-8d85-187a5a908441" />

---

## 🔗 APIs Utilizadas

- **Jamendo API** — https://api.jamendo.com/v3.0/ · Música gratuita bajo licencia Creative Commons
- **Firebase Realtime Database** — Comunicación en tiempo real entre dispositivos

---

## 📄 Licencia

Proyecto académico desarrollado para la materia **Desarrollo para Dispositivos Inteligentes** — UTNG, periodo Mayo–Agosto 2026.
