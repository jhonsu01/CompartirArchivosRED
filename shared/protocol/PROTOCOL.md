# Protocolo de Comunicación — Compartir Archivos RED

> Versión del protocolo: **1**
> Implementado idénticamente por el cliente Android (Kotlin) y el cliente Windows (.NET WPF)
> para garantizar interoperabilidad total.

## Puertos

| Constante         | Valor   | Transporte | Uso                                  |
| ----------------- | ------- | ---------- | ------------------------------------ |
| `DISCOVERY_PORT`  | `45454` | UDP        | Descubrimiento por broadcast en LAN  |
| `TRANSFER_PORT`   | `45455` | TCP        | Emparejamiento (PIN) + transferencia |

## 1. Descubrimiento (UDP broadcast)

Cada dispositivo:

- **Anuncia** cada ~2 s un datagrama UDP a `255.255.255.255:45454`.
- **Escucha** en `0.0.0.0:45454` y mantiene una lista de peers, expirando los que
  no re-anuncian en ~10 s.

Formato del anuncio (JSON, un objeto por datagrama):

```json
{ "v": 1, "type": "ANNOUNCE", "id": "<uuid>", "name": "<nombre dispositivo>", "platform": "windows|android", "port": 45455 }
```

- `id`: identificador estable del dispositivo (UUID por instalación).
- `port`: puerto TCP donde ese dispositivo acepta transferencias.

> En Android se adquiere un `MulticastLock` para poder recibir broadcasts.

## 2. Emparejamiento + Transferencia (TCP)

Mensajes de control = **JSON delimitado por salto de línea (`\n`)**, UTF-8.
Los **bytes crudos** del contenido de cada archivo se envían inmediatamente
después de su cabecera `FILE`.

### Secuencia

```
Emisor (S)                                  Receptor (R)
    |                                            |
    |---- TCP connect a R:45455 ---------------->|
    |                                            |
    |---- OFFER (name, fileCount, totalSize) --->|
    |                                            |  R genera PIN de 6 dígitos,
    |                                            |  lo MUESTRA en su pantalla,
    |<--- PIN_REQUIRED ---------------------------|  y espera.
    |                                            |
    |  S pide al usuario el PIN mostrado en R    |
    |---- PIN (pin) ---------------------------->|
    |                                            |  R valida PIN + expiración (60 s)
    |                                            |  y consentimiento del usuario.
    |<--- ACCEPTED  (o REJECTED, reason) ---------|
    |                                            |
    |  Por cada archivo:                         |
    |---- FILE (name, size) -------------------->|
    |---- <size> bytes crudos ----------------->|  R guarda en carpeta de descargas
    |            ...                             |
    |---- DONE --------------------------------->|
    |                                            |
    |------------- cierre de conexión -----------|
```

### Mensajes

| type          | Dirección | Campos                                  |
| ------------- | --------- | --------------------------------------- |
| `OFFER`       | S → R     | `name`, `fileCount`, `totalSize`        |
| `PIN_REQUIRED`| R → S     | —                                       |
| `PIN`         | S → R     | `pin` (string de 6 dígitos)             |
| `ACCEPTED`    | R → S     | —                                       |
| `REJECTED`    | R → S     | `reason` (`bad_pin`, `expired`, `denied`)|
| `FILE`        | S → R     | `name`, `size` (bytes)                   |
| `DONE`        | S → R     | —                                       |

Todos los mensajes incluyen `"v": 1` y `"type"`.

## 3. Seguridad (v0.1.0)

- **PIN obligatorio** de 6 dígitos, generado por el receptor, con expiración de 60 s.
- Conexiones sin PIN válido se rechazan y cierran.
- **Roadmap:** cifrado TLS del canal TCP (hoy el canal es en claro dentro de la LAN).

## 4. Nombres de archivo

El receptor **sanea** el nombre recibido (quita rutas/`..`) y, si ya existe,
añade sufijo `(1)`, `(2)`, … para no sobrescribir.
