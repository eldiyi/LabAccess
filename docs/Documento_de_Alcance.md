# Documento de alcance - LabAccess

## Apartado 1. La aplicación

LabAccess (Control de Acceso a Salas Críticas) es una aplicación móvil para controlar la entrada a salas de alta criticidad de un laboratorio. Permite al personal de seguridad verificar y auditar quién accede a cada sala y a qué hora, y saber qué efectivos están al mando.

La usan los guardias y vigilantes desde sus terminales en los puntos de paso, y los administradores de seguridad que supervisan la trazabilidad. Combina validación de identidad, consulta de registros mediante una API remota, almacenamiento local para seguir operando si cae la red y el uso de la cámara para leer códigos QR o credenciales de paso.

**Estado actual:** la identidad se valida contra el backend común de la asignatura, que hace de directorio corporativo y de servidor de control. La aplicación solicita el permiso de cámara, pero la lectura de códigos QR aún no está implementada.

## Apartado 2. Datos que maneja

| Dato | Dónde se guarda hoy | Quién puede leerlo hoy | Cómo se protegerá |
|---|---|---|---|
| Token de sesión | `shared_prefs/sesion.xml`, en claro. Logcat. Tráfico HTTP. | Cualquiera con adb y depuración activada. Observador en la red. | A completar en el Bloque III |
| Contraseña del vigilante | Fuera del disco de la aplicación. Logcat. Tráfico HTTP. En el servidor, sin resumen. | Cualquiera con adb y depuración activada. Observador en la red. Quien acceda al servidor. | A completar en el Bloque III |
| Registros de acceso | `labaccess.db`, `-wal` y `-shm`, sin cifrar. Logcat. Tráfico HTTP. | Cualquiera con adb y depuración activada. Observador en la red. Otro vigilante con token válido. | A completar en el Bloque III |
| Identidad del vigilante (usuario, nombre y rol) | `sesion.xml` y columna `vigilante` (usuario). Logcat y tráfico HTTP (todo). | Cualquiera con adb y depuración activada. Observador en la red. Cualquier usuario con token válido. | A completar en el Bloque III |
| Correo y DNI | Sólo en el servidor: ninguna pantalla los pide. Se sirven por HTTP. | Quien tenga el token de ese vigilante. Observador en la red si se piden. | A completar en el Bloque III |
| Metadatos de uso | Registro del servidor. Logcat. Tráfico de red. | Quien acceda al servidor. Cualquiera con adb y depuración activada. Observador en la red. | A completar en el Bloque III |

## Apartado 3. Superficie expuesta

| Vía de entrada | Cómo se invoca hoy | Qué comprueba hoy | Se trata en |
|---|---|---|---|
| Activity principal exportada | `adb shell am start -n` | Nada | Sesión 3.4 |
| Enlace profundo `appsegura://detalle/{id}` | Enlace web, mensaje u otra aplicación | Nada en la interfaz | Sesión 3.4 |
| Llamadas a la API | `curl` con un token válido | Sólo la presencia del token | Sesión 3.5, P3.2 (el canal) y Bloque IV (la autorización) |
| Almacenamiento local | `adb run-as` | Nada: está en claro | Sesión 3.3 y P3.1 |
| Registros | `adb logcat` | Nada | Sesión 3.6 |
| Vista de recientes | Botón del sistema | Nada: `FLAG_SECURE` no está activada | Sesión 3.6 |
| Permiso de cámara | Botón del listado | Permiso en tiempo de ejecución | Sesión 3.4 y P3.3 |

La navegación y las pantallas no comprueban la sesión: la única comprobación de la aplicación está en el repositorio, que exige un token. El servidor sólo comprueba que ese token exista, no de quién es cada registro. La lectura de códigos QR se añadirá a esta tabla cuando se implemente.

## Apartado 4. Modelo de amenazas preliminar

1. **Intruso o empleado sin autorización.** Quiere entrar en la sala, ocultar que estuvo dentro o saber quién está de guardia en cada turno. Podría pedir al servidor registros ajenos, porque no comprueba a quién pertenecen o, cuando exista la lectura de códigos, presentar uno manipulado en el punto de control.
2. **Alguien conectado a la red del laboratorio.** Busca las contraseñas de los vigilantes, sus sesiones activas y la lista de personas que entran y salen. Hoy le basta con escuchar la red, porque todo viaja sin cifrar.
3. **Quien tiene el terminal en la mano.** Alguien que lo encuentra desbloqueado en la garita, o el compañero del relevo. Podría ver información en la lista de aplicaciones recientes, abrir pantallas internas o conectarlo por cable para sacar las sesiones y el historial de turnos anteriores.
4. **Una aplicación maliciosa en el mismo terminal.** Sin permisos especiales, podría abrir directamente pantallas de registros concretos mientras haya una sesión activa del vigilante.

## Apartado 5. Reparto de trabajo

| Miembro del equipo | Requisito técnico mínimo | Responsabilidad |
|---|---|---|
| Jorge Manuel Gutiérrez Melgar | Autenticación de usuario | Inicio de sesión, estado de acceso y cierre seguro de sesión |
| Ignacio Ferrer Téllez | Almacenamiento local de datos sensibles | Persistencia segura de tokens en preferencias y de registros en SQLite |
| Eduardo Jiliberto Cañizal | Comunicación con API remota | Cliente HTTP, cabeceras de autorización y protección del canal |
| Pablo Cervantes Pardo | Permiso peligroso justificado | Permiso de cámara en tiempo de ejecución y degradación elegante |

Cada responsable mantiene su parte en ramas de funcionalidad, integradas en la rama principal tras revisión, sin que ninguna clave ni credencial quede en el historial del repositorio.
