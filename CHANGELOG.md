# Changelog

Todas las novedades relevantes de este proyecto se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

## [0.3.0] - 2026-07-02

### Añadido

- **Android: cambiar la carpeta de recepción** de archivos desde la app (selector SAF, se recuerda entre
  sesiones). Los archivos entrantes se guardan en la carpeta elegida; si no se elige ninguna, en la carpeta
  interna de la app.
- **Explorador interno: botón "Abrir"** que abre el archivo seleccionado con una app instalada compatible
  (vía FileProvider + selector "Abrir con").
- **Explorador interno**: las filas muestran fecha de modificación además del tamaño.

### Cambiado

- **Interfaz Android más intuitiva**: tarjeta de carpeta de recepción, dispositivos con icono por plataforma
  (🖥️/📱) e indicador de selección, y disposición más clara.

## [0.2.0] - 2026-07-02

### Añadido

- **Explorador de archivos interno** en Android (navegación por carpetas, selección múltiple),
  imprescindible en Android TV donde no existe selector de documentos del sistema.
- App de escritorio: **cambiar la carpeta de descargas** desde la interfaz (se recuerda entre sesiones).
- App de escritorio: **icono** propio (visible en el instalador, accesos directos y ventana).
- Instalador Windows: **excepción de firewall** automática (permite recibir archivos en la LAN).

### Corregido

- **Descubrimiento del equipo Windows** en redes con varias interfaces: ahora emite el anuncio por la
  dirección de broadcast de cada interfaz, de modo que los móviles/TV **sí ven el PC de escritorio** y
  pueden enviarle archivos.
- **Android TV**: el envío ya no falla con "ninguna aplicación puede procesar esta acción" (usa el
  explorador interno).
- App de escritorio: **botones que se cortaban** (tamaños mínimos de ventana y layout ajustado).

## [0.1.0] - 2026-07-02

### Añadido

- **MVP Fase 1 + 2** funcional en Android y Windows.
- Descubrimiento automático de dispositivos en LAN vía UDP broadcast (puerto 45454).
- Transferencia de archivos por TCP (puerto 45455) con múltiples archivos y barra de progreso.
- Emparejamiento seguro mediante PIN de 6 dígitos con expiración de 60 s.
- Cliente **Android** (Kotlin + Jetpack Compose, Material 3, modo oscuro).
- Cliente **Windows** (.NET 8 + WPF, tema oscuro) con instalador **MSI** (WiX).
- Protocolo común documentado en `shared/protocol/PROTOCOL.md`.
- CI/CD con GitHub Actions: build + publicación automática de **APK** y **MSI** en cada release (tag `v*`).

[Unreleased]: https://github.com/jhonsu01/CompartirArchivosRED/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/jhonsu01/CompartirArchivosRED/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/jhonsu01/CompartirArchivosRED/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/jhonsu01/CompartirArchivosRED/releases/tag/v0.1.0
