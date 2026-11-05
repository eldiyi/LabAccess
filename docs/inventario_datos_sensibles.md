# Inventario de datos sensibles · LabAccess

Entregable de la sección 6.2 de P2.2. La tabla se incorpora al apartado 2 del
[documento de alcance](Documento_de_Alcance.md). La última columna se completa en
el Bloque III.

| Dato | Dónde se guarda hoy | Quién puede leerlo hoy | Cómo se protegerá |
|---|---|---|---|
| **Token de sesión** del vigilante | `shared_prefs/sesion.xml`, en claro. Logcat. Tráfico HTTP sin cifrar. | Cualquiera con adb y depuración activada. Cualquier observador en la red. | A completar en el Bloque III |
| **Contraseña** del vigilante | No se guarda en el disco de la aplicación. Logcat. Tráfico HTTP sin cifrar. En el servidor, sin resumen ni sal. | Cualquiera con adb y depuración activada. Cualquier observador en la red. Quien tenga acceso al servidor. | A completar en el Bloque III |
| **Registros de acceso** (persona, detalle y fecha) | `databases/labaccess.db`, `-wal` y `-shm`, tabla `registros_acceso`, sin cifrar. Logcat. Tráfico HTTP sin cifrar. | Cualquiera con adb y depuración activada. Cualquier observador en la red. Cualquier otro vigilante con un token válido, porque `GET /api/elementos/{id}` no comprueba de quién es el registro. | A completar en el Bloque III |
| **Identidad del vigilante** (usuario, nombre y rol) | Usuario: `sesion.xml` y columna `vigilante` de la base de datos. Usuario, nombre y rol: logcat y tráfico HTTP sin cifrar. | Cualquiera con adb y depuración activada. Cualquier observador en la red. Cualquier usuario con un token válido, porque `GET /api/admin/usuarios` no comprueba el rol; ese listado añade el correo. | A completar en el Bloque III |
| **Datos personales** del vigilante (correo y DNI) | Hoy no llegan al dispositivo: `obtenerPerfil()` existe, pero ninguna pantalla la llama. Sólo están en el servidor, que los entrega por HTTP sin cifrar en `GET /api/perfil`. | Quien tenga el token de ese vigilante. Cualquier observador en la red si se llegara a hacer la petición. | A completar en el Bloque III |
| **Metadatos de uso** (quién llama, cuándo, a qué ruta y con qué resultado) | Registro del servidor. Logcat. Tráfico de red. | Quien tenga acceso al servidor. Cualquiera con adb y depuración activada. Cualquier observador en la red. Permiten deducir los horarios y turnos de cada vigilante. | A completar en el Bloque III |

Los datos que devuelve hoy el backend son los de ejemplo de la asignatura, no
registros reales de una sala. La clasificación se hace por lo que esos campos
contendrán en el dominio: quién entra, en qué sala y a qué hora.

## Datos previstos que aún no existen

No se incluyen en la tabla porque hoy no hay código que los maneje. Hay que
añadirlos cuando se implementen:

- Contenido de los códigos QR y credenciales leídas con la cámara (P2.3).
- Claves de apertura, si el proyecto las llega a incorporar.
