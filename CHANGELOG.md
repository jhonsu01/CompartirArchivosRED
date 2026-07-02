# Changelog

Todas las novedades relevantes de este proyecto se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

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

[Unreleased]: https://github.com/USUARIO/CompartirArchivosRED/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/USUARIO/CompartirArchivosRED/releases/tag/v0.1.0
