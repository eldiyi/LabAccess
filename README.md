# LabAccess - Control de Acceso a Salas Críticas

## 1. Información General
* **Equipo**: Grupo LabAccess (Dominio Acceso)
* **Miembros y Responsabilidades**:
  * **Jorge Manuel Gutiérrez Melgar**: Autenticación de usuario
  * **Ignacio Ferrer Téllez**: Almacenamiento local de datos sensibles
  * **Eduardo Jiliberto Cañizal**: Comunicación con API remota
  * **Pablo Cervantes Pardo**: Permiso peligroso justificado
* **Dominio asignado**: Control de acceso a instalaciones (Grupos F y M)

---

## 2. Descripción de la Aplicación y Alcance (Apartado 5.1)
Nuestra aplicación será utilizada por el personal de seguridad de un laboratorio para ejercer el control de acceso del personal a una sala crítica, a la vez que mantiene un registro y monitorización centralizados de los efectivos a cargo en cada momento, qué personas entraron y a qué horas. 

Dentro de toda la información gestionada, consideramos que el **dato más sensible** son las **credenciales de acceso/tokens temporales del personal de seguridad** y el **registro histórico local de entradas a la sala crítica**, ya que su manipulación o exfiltración comprometería la trazabilidad de accesos y la integridad de las instalaciones protegidas.

### Requisitos Técnicos Mínimos
* **Autenticación de usuario**: Inicio de sesión del guardia/vigilante de seguridad contra el directorio corporativo (Active Directory / Microsoft Entra ID) para verificar su identidad y puesto de servicio antes de habilitar la consola de paso. — **Responsable:** *Jorge Manuel Gutiérrez Melgar*
* **Almacenamiento local de datos sensibles**: Persistencia local segura de los tokens de sesión de Active Directory (*Refresh/Access Tokens*), claves criptográficas de apertura y la cola de registros de paso (fichajes locales) en caso de caída temporal de red. — **Responsable:** *Ignacio Ferrer Téllez*
* **Llamada a una API remota**: Envío y sincronización periódica de los eventos de acceso y consulta en tiempo real al endpoint del directorio activo / servidor de control para verificar autorizaciones de empleados. — **Responsable:** *Eduardo Jiliberto Cañizal*
* **Al menos un permiso peligroso justificado**: Solicitud del permiso de hardware `android.permission.CAMERA` para el escaneo de los códigos QR o credenciales de entrada del personal en el acceso a la sala. — **Responsable:** *Pablo Cervantes Pardo*

---

## 3. Convenciones del Equipo (Apartado 5.2)

| Concepto | Valor acordado | Justificación técnica |
| :--- | :--- | :--- |
| **Nombre de paquete** | `es.uma.seguridad.labaccess` (`labaccess`) | Identificador único canónico en el sistema para evitar colisiones de instalación y mantener la nomenclatura académica requerida. |
| **`minSdk`** | `34` (Android 14) | Las empresas suelen utilizar gestores de contraseñas de terceros para el resto de sus operativas corporativas (no todas emplean *Google Password Manager*). A partir de API 34, el sistema estandariza la integración con gestores externos mediante la API nativa de *Credential Manager*. |
| **`targetSdk`** | `36` (Android 16) | Adopción formal de la última línea base del sistema según las directivas de publicación de Google Play y las restricciones de seguridad más recientes de Android (blindaje estricto de Intents, bloqueo de invocaciones de componentes en segundo plano y compatibilidad con las protecciones de memoria más recientes). |
| **Ramas y revisión** | Modelo Feature-Branch (`feature/*` -> `main`) | Cada integrante desarrollará su requisito/componente en una rama independiente. Solo se integrará en la rama principal tras haber sido comprobado, probado y revisado por el resto del equipo, evitando roturas en la base funcional del proyecto. |
| **Responsables** | Asignación directa nominal | Asignación unívoca de cada requisito técnico mínimo para garantizar la autoría y defensa técnica en las fases de construcción (Bloque II) y verificación (Bloque IV). |

---

## 4. Reconocimiento de Aplicación Real (Apartado 5.4)
Se ha seleccionado e inspeccionado la aplicación del dominio de control de acceso corporativo **Sigur Access App** (`com.sigursys.accessapp`) descargada de Google Play Store.

### Tabla de Observaciones Técnicas

| Qué mirar | Con qué herramienta | Observación del equipo |
| :--- | :--- | :--- |
| **Permisos declarados** | `aapt2 dump permissions` | Declara permisos de hardware y comunicación: `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `NFC`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (con flag `neverForLocation`). Además, declara permisos heredados con versión acotada: `ACCESS_COARSE_LOCATION` (hasta API 28), `ACCESS_FINE_LOCATION` (hasta API 30), `BLUETOOTH` y `BLUETOOTH_ADMIN` (hasta API 30). |
| **Componentes exportados** | `apktool` + análisis del manifiesto | **Activities exportadas (2):** `com.sigursys.accessapp.MainActivity` y `com.sigursys.accessapp.MainActivityApi31`<br>**BroadcastReceiver exportado (1):** `androidx.profileInstaller.ProfileInstallReceiver`<br>**Service exportado (1):** `com.sigur.android.accessframework.AccessHostApduService` |
| **Esquemas de firma** | `apksigner verify --print-certs -v` | Firmado mediante clave de Google Play (Play App Signing). Titular: `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US`. SHA-256: `570c2ef7f33f6aa72a71a4e9fdd055538c1d2d8e650607fec36f09f745589048`. Incluye Source Stamp Signer de Google (`SHA-256: 3257d599...`). Esquemas de firma verificados. |
| **`minSdk` y `targetSdk`** | `aapt2 dump badging` | `minSdkVersion: '32'` (Android 12L)<br>`targetSdkVersion: '37'` |
| **Número de piezas instaladas** | `adb shell pm path` | **5 piezas (Split APKs / Android App Bundle):**<br>1. `base.apk`<br>2. `split_ble.apk` (módulo dinámico Bluetooth Low Energy)<br>3. `split_hce.apk` (módulo dinámico Host Card Emulation NFC)<br>4. `split_config.en.apk` (recursos de idioma)<br>5. `split_config.xxhdpi.apk` (densidad gráfica) |

### Para el cuaderno del equipo (Reflexiones de evaluación)
* **Justificación de permisos observados:** La aplicación utiliza Bluetooth Low Energy (BLE) y NFC para interactuar con cerraduras y tornos físicos. Es destacable el uso de buenas prácticas de retrocompatibilidad: los permisos invasivos de ubicación (`ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION`) están restringidos con `maxSdkVersion='30'`, ya que en versiones modernas de Android (API 31+) se sustituyeron por los permisos no relacionados con geolocalización `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT`.
* **Componentes exportados:**
  * `MainActivity` y `MainActivityApi31`: Puntos de entrada para el usuario y compatibilidad con trampolines de splash screen de Android 12+.
  * `AccessHostApduService`: Servicio crítico exportado que implementa el protocolo HCE (Host-based Card Emulation) para intercambiar tramas APDU con los terminales de control de acceso NFC físicos externos.
* **Estructura modular (Split APKs):** El uso de splits permite aislar dependencias pesadas de hardware (BLE y HCE) en módulos cargados dinámicamente según las capacidades del dispositivo, reduciendo el tamaño del artefacto y la superficie expuesta.

---

## 5. Higiene del Repositorio y Git (Apartado 5.3)
* El repositorio cuenta con un fichero `.gitignore` configurado que excluye directorios generados (`/build`, `.gradle`), ficheros locales de entorno (`local.properties`) y cualquier fichero de almacén de claves (`*.jks`, `*.keystore`).
* Se respeta la política de **cero secretos en el histórico de commits**, evitando el registro de credenciales o tokens en claro en las revisiones del código.