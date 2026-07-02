# Guía de Desarrollo con IA: App de Transferencia de Archivos (Android + Windows)

## 1. Objetivo
Desarrollar un sistema multiplataforma que permita:
- Compartir y recibir archivos en red local (LAN/WiFi).
- Detección automática de dispositivos.
- Emparejamiento seguro mediante PIN.
- Explorador de archivos integrado (inspirado en Fossify File Manager).
- Aplicaciones:
  - Android (móvil + Android TV)
  - Windows (.msi instalable)

---

## 2. Arquitectura General

### Componentes
- Cliente Android (Kotlin)
- Cliente Windows (C# .NET / Electron opcional)
- Servicio de descubrimiento (mDNS / UDP Broadcast)
- Servicio de transferencia (TCP/HTTP/QUIC)

### Flujo básico
1. Descubrimiento automático en red
2. Solicitud de conexión
3. Generación de PIN
4. Validación de emparejamiento
5. Transferencia segura

---

## 3. Tecnologías Recomendadas

### Android
- Lenguaje: Kotlin
- UI: Jetpack Compose
- Networking: Ktor / OkHttp
- File system: SAF (Storage Access Framework)
- TV UI: Leanback / Compose TV

### Windows
- Opción 1: .NET (WPF / WinUI)
- Opción 2: Electron (Node.js)
- Empaquetado: MSI (WiX Toolset)

### Red
- Descubrimiento: mDNS (NSD Android) / UDP Broadcast
- Transferencia: TCP sockets o HTTP server embebido
- Seguridad: TLS + PIN temporal

---

## 4. Funcionalidades Clave

### 4.1 Descubrimiento Automático
- Broadcast en red local
- Lista dinámica de dispositivos disponibles

### 4.2 Emparejamiento Seguro
- Generación de PIN aleatorio (6 dígitos)
- Expiración del PIN (30-60 segundos)
- Validación en ambos dispositivos

### 4.3 Transferencia de Archivos
- Envío múltiple
- Barra de progreso
- Reintentos automáticos
- Soporte para archivos grandes (>2GB)

### 4.4 Explorador de Archivos
Inspirado en Fossify:
- Navegación por carpetas
- Vista lista/cuadrícula
- Búsqueda
- Selección múltiple
- Acceso a almacenamiento interno/externo

### 4.5 Gestión de Conexiones
- Lista de dispositivos conectados
- Aceptar / rechazar conexiones
- Historial de transferencias

---

## 5. UI/UX

### Tema
- Modo oscuro obligatorio
- Material Design 3

### Adaptación por dispositivo

#### Móvil
- Navegación inferior
- Gestos táctiles

#### Android TV
- Navegación con control remoto
- Enfoque (focus navigation)
- Iconos grandes

### Iconos Dinámicos
- Detectar tipo de dispositivo
- Cambiar densidad y tamaño:
  - Móvil → iconos compactos
  - TV → iconos grandes y espaciados

---

## 6. Seguridad

- PIN de emparejamiento obligatorio
- Conexiones no autenticadas bloqueadas
- Cifrado TLS
- Validación de origen
- Permisos mínimos requeridos

---

## 7. IA en el Desarrollo

### Uso de IA
- Generación de código base
- Refactorización automática
- Generación de UI
- Testing automatizado

### Prompts sugeridos
- "Genera un servicio Android para descubrimiento mDNS"
- "Crea un file explorer en Jetpack Compose"
- "Implementa transferencia de archivos vía sockets en Kotlin"

---

## 8. Estructura del Proyecto

/android
  /ui
  /network
  /explorer
  /pairing

/windows
  /ui
  /network
  /installer

/shared
  /protocol
  /security

---

## 9. Protocolo de Comunicación

### Ejemplo JSON
{
  "type": "PAIR_REQUEST",
  "device": "Android-TV",
  "pin": "123456"
}

---

## 10. Roadmap

### Fase 1
- Descubrimiento
- Transferencia básica

### Fase 2
- PIN y seguridad

### Fase 3
- Explorador de archivos

### Fase 4
- UI adaptativa (TV + móvil)

### Fase 5
- Optimización y publicación

---

## 11. Publicación

### Android
- APK / AAB
- Google Play

### Windows
- MSI instalador
- Firma digital

---

## 12. Recomendaciones Finales

- Priorizar estabilidad de red
- Minimizar latencia
- UX simple tipo “Send Files to TV”
- Código modular
- Pruebas en múltiples dispositivos

