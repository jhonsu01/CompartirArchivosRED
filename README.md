<div align="center">

# 📡 Compartir Archivos RED

**Transferencia de archivos en red local (LAN/WiFi) entre Android y Windows**
Descubrimiento automático · Emparejamiento por PIN · Sin nube, sin cables

[![Release](https://img.shields.io/github/v/release/jhonsu01/CompartirArchivosRED?label=última%20versión)](https://github.com/jhonsu01/CompartirArchivosRED/releases)
[![CI](https://github.com/jhonsu01/CompartirArchivosRED/actions/workflows/ci.yml/badge.svg)](https://github.com/jhonsu01/CompartirArchivosRED/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-informational.svg)](LICENSE)

</div>

---

## ✨ Características (v0.1.0 — MVP)

- 🔎 **Descubrimiento automático** de dispositivos en la misma red (UDP broadcast).
- 🔐 **Emparejamiento seguro por PIN** de 6 dígitos con expiración.
- 📤 **Transferencia de archivos** por TCP: múltiples archivos, barra de progreso, archivos grandes.
- 🌙 **Modo oscuro** (Material 3 en Android, tema oscuro en Windows).
- 🖥️📱 **Multiplataforma**: cliente **Android** (móvil) y cliente **Windows** (`.msi`).

> Roadmap: explorador de archivos tipo Fossify, cifrado TLS, soporte Android TV, historial. Ver [`docs/guia_app_transferencia_archivos.md`](docs/guia_app_transferencia_archivos.md).

## 📦 Descargas

Descarga la última versión desde la página de **[Releases](https://github.com/jhonsu01/CompartirArchivosRED/releases)**:

| Plataforma | Archivo                              |
| ---------- | ------------------------------------ |
| Android    | `CompartirArchivosRED-<versión>.apk` |
| Windows    | `CompartirArchivosRED-<versión>.msi` |

Los binarios se compilan y publican **automáticamente** por GitHub Actions en cada tag `v*`.

## 🚀 Uso rápido

1. Instala la app en ambos dispositivos (mismo WiFi/LAN).
2. Ábrela: cada dispositivo se anuncia y aparece en la lista del otro.
3. En el **emisor**, pulsa *Enviar*, elige archivos y selecciona el dispositivo destino.
4. El **receptor** muestra un **PIN**; escríbelo en el emisor para autorizar.
5. La transferencia comienza; los archivos llegan a la carpeta de descargas del receptor.

## 🗂️ Estructura del repositorio

```
├── android/            App Android (Kotlin + Jetpack Compose)
├── windows/            App Windows (.NET 8 WPF) + instalador WiX (MSI)
├── shared/protocol/    Protocolo común documentado (PROTOCOL.md)
├── docs/               Guía de desarrollo y documentación
├── .github/workflows/  CI/CD (build + release automático de APK y MSI)
├── VERSION             Versión semántica actual (fuente de verdad)
└── CHANGELOG.md        Historial de cambios
```

## 🛠️ Compilar localmente

### Windows (`.msi`)

Requisitos: **.NET SDK 8**, **WiX Toolset 5** (`dotnet tool install --global wix`).

```powershell
pwsh windows/build-msi.ps1 -Version 0.1.0
# Resultado: dist/CompartirArchivosRED-0.1.0.msi
```

### Android (`.apk`)

Requisitos: **JDK 17**, **Android SDK** (o abrir `android/` en Android Studio).

```bash
cd android
./gradlew assembleDebug
# Resultado: android/app/build/outputs/apk/debug/app-debug.apk
```

## 📈 Versionado

- La versión vive en [`VERSION`](VERSION) y en `CHANGELOG.md`.
- Para publicar: sube la versión, crea el tag `vX.Y.Z` y haz push del tag →
  GitHub Actions compila y crea la Release con el APK y el MSI adjuntos.
- **Solo se mantiene la última release:** al publicar una nueva versión, el workflow
  elimina automáticamente las releases (y tags) anteriores, dejando únicamente la más reciente.

## 🔒 Protocolo y seguridad

Ver [`shared/protocol/PROTOCOL.md`](shared/protocol/PROTOCOL.md). El emparejamiento
requiere PIN; el canal de datos se moverá a TLS en una versión futura.

## 📄 Licencia

[MIT](LICENSE) © 2026 Jhon Supelano
