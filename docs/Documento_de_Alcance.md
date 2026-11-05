# Documento de alcance - LabAccess

> **Borrador actualizado con la parte de grupo de P2.2.** El apartado 2 recoge el
> inventario de datos sensibles. Lo marcado como *(P2.3)* describe vías que aún no están implementadas y debe comprobarse
> al terminar esa práctica, antes de entregar este documento.

## Apartado 1. La aplicación

LabAccess (Control de Acceso a Salas Críticas) es una aplicación móvil diseñada
para el dominio de control de acceso a instalaciones críticas de laboratorio. Su
propósito principal es permitir al personal de seguridad y vigilancia gestionar,
verificar y auditar en tiempo real la entrada de empleados autorizados a salas de
alta criticidad, manteniendo un registro centralizado y local de qué efectivos se
encuentran al mando, qué personas han accedido a las instalaciones y en qué franja
horaria exacta se produjo cada movimiento.

Los usuarios principales de la aplicación son los guardias y vigilantes de
seguridad del laboratorio desde sus terminales de control en los puntos de paso,
así como los administradores de seguridad encargados de supervisar la trazabilidad
de los accesos. La aplicación combina la validación de identidad contra el
directorio corporativo, la consulta y sincronización de autorizaciones mediante
una API remota, el almacenamiento local para garantizar la continuidad operativa
ante caídas temporales de red y el uso de la cámara del dispositivo para agilizar
la lectura de códigos QR o credenciales de paso del personal.

**Estado actual:** la identidad se valida contra el backend común de la asignatura,
que hace de directorio corporativo y de servidor de control. La cámara se
incorpora en P2.3.

## Apartado 2. Datos que maneja (Inventario de datos sensibles)

| Dato | Dónde se guarda hoy | Quién puede leerlo hoy | Cómo se protegerá |
|---|---|---|---|
| Token de sesión del vigilante | `shared_prefs/sesion.xml`, en claro. Logcat. Tráfico HTTP. | Cualquiera con adb y depuración activada. Observador en la red. | A completar en el Bloque III |
| Contraseña del vigilante | No se guarda en el disco de la aplicación. Logcat. Tráfico HTTP. En el servidor, sin resumen. | Cualquiera con adb y depuración activada. Observador en la red. Quien acceda al servidor. | A completar en el Bloque III |
| Registros de acceso (persona, detalle y fecha) | `labaccess.db`, `-wal` y `-shm`, tabla `registros_acceso`, sin cifrar. Logcat. Tráfico HTTP. | Cualquiera con adb y depuración activada. Observador en la red. Otro vigilante con token válido. | A completar en el Bloque III |
| Identidad del vigilante (usuario, nombre y rol) | `sesion.xml` y columna `vigilante` (usuario). Logcat y tráfico HTTP (todo). | Cualquiera con adb y depuración activada. Observador en la red. Cualquier usuario con token válido. | A completar en el Bloque III |
| Datos personales (correo y DNI) | Hoy no llegan al dispositivo: ninguna pantalla los pide. Sólo en el servidor, servidos por HTTP. | Quien tenga el token de ese vigilante. Observador en la red si se piden. | A completar en el Bloque III |
| Metadatos de uso (quién, cuándo y qué ruta) | Registro del servidor. Logcat. Tráfico de red. | Quien acceda al servidor. Cualquiera con adb y depuración activada. Observador en la red. | A completar en el Bloque III |

- **Exposición actual de los datos:** el token de sesión del vigilante se guarda en
  texto plano en `shared_prefs/sesion.xml` y se imprime en el registro del sistema
  al registrar la respuesta de acceso y el cuerpo de las peticiones HTTP. El
  histórico local de entradas a la sala crítica permanece sin cifrar en la tabla
  `registros_acceso` (incluido el fichero de escritura anticipada
  `labaccess.db-wal`) y viaja en claro por HTTP entre el dispositivo y el servidor.
## Apartado 3. Superficie expuesta

| Vía de entrada | Cómo se invoca hoy | Qué comprueba hoy | Se trata en |
|---|---|---|---|
| Activity principal exportada | Comando `adb shell am start -n` | Nada | Sesión 3.4 |
| Enlace profundo de detalle *(P2.3)* | Enlace web u otra aplicación | Nada en la interfaz | Sesión 3.4 |
| Llamadas a la API remota | Petición `curl` con token | Sólo la presencia del token | Sesión 3.5, P3.2 y Bloque IV |
| Almacenamiento local | Comando `adb run-as` | Nada: texto en claro | Sesión 3.3 y P3.1 |
| Registros del sistema | Comando `adb logcat` | Nada | Sesión 3.6 |
| Vista de recientes | Botón del sistema | Nada hoy; `FLAG_SECURE` se añade en P2.3 | Sesión 3.6 |
| Escáner de cámara *(P2.3)* | Lanzador de permiso | Permiso en ejecución | Sesión 3.4 y P3.3 |
| Lectura de código QR *(P2.3)* | Captura óptica externa | Nada sobre el contenido | Sesión 3.4 y P3.3 |

- **Resumen de vectores de entrada:** la superficie expuesta abarca los puntos de
  entrada por componentes exportados y, desde P2.3, enlaces profundos (esquema
  `appsegura://detalle/{id}`), donde la interfaz y la navegación no bloquean el
  acceso y delegan la detención exclusivamente en la comprobación del token dentro
  del repositorio local. En el plano de red y sistema, la API remota carece de
  control de autorización por propietario del registro, el canal HTTP transmite la
  información sin cifrar y el dispositivo expone trazas por logcat y por el
  almacenamiento interno accesible en modo depuración.

## Apartado 4. Modelo de amenazas preliminar

1. **Intruso o empleado no autorizado que intenta acceder o alterar la trazabilidad
   de la sala crítica:** un atacante externo o un trabajador sin permisos para
   entrar en la sala crítica buscaría suplantar una identidad autorizada, manipular
   los registros de entrada para ocultar su presencia física en el laboratorio o
   consultar qué personal de seguridad está de guardia en cada turno. Para ello,
   podría enviar peticiones directas al servidor aprovechando que el backend no
   comprueba a quién pertenece cada registro o presentar un código QR manipulado en
   el punto de control.
2. **Atacante conectado a la red local del laboratorio:** una persona con acceso a
   la red Wi-Fi o de datos donde operan los terminales de vigilancia buscaría
   interceptar las comunicaciones entre la aplicación móvil y el servidor de
   control. Al viajar el tráfico en claro por HTTP, el atacante obtendría sin
   esfuerzo las contraseñas de los guardias de seguridad, sus tokens de sesión
   activos y el listado detallado de personas que entran y salen de la instalación
   crítica.
3. **Acceso físico temporal al terminal de vigilancia o relevo de turno:** un
   tercero que encuentre el teléfono o tableta de seguridad desbloqueado en la
   garita, o bien otro operario que tome el relevo en el mismo dispositivo
   compartido, podría intentar observar información confidencial en la vista de
   aplicaciones recientes, abrir pantallas internas mediante enlaces profundos o
   conectar el terminal por cable USB para extraer de la base de datos local y de
   los registros del sistema los tokens y el historial de entradas de turnos
   anteriores.
4. **Aplicación maliciosa instalada en el mismo dispositivo:** otra aplicación sin
   privilegios especiales instalada en el terminal de seguridad podría invocar el
   enlace profundo declarado en el manifiesto para forzar la apertura de registros
   concretos de acceso o intentar abusar de componentes exportados cuando exista
   una sesión activa del vigilante.

## Apartado 5. Reparto de trabajo

| Miembro del equipo | Requisito técnico mínimo asignado | Alcance de la responsabilidad |
|---|---|---|
| Jorge Manuel Gutiérrez Melgar | Autenticación de usuario | Inicio de sesión del vigilante, gestión del estado de acceso y cierre seguro de sesión |
| Ignacio Ferrer Téllez | Almacenamiento local de datos sensibles | Persistencia segura de tokens en preferencias y de registros de paso en base de datos SQLite |
| Eduardo Jiliberto Cañizal | Comunicación con API remota | Sincronización de eventos de acceso, cliente HTTP, cabeceras de autorización y protección del canal |
| Pablo Cervantes Pardo | Permiso peligroso justificado | Solicitud en tiempo de ejecución del permiso de cámara para escaneo QR y degradación elegante |

- **Coordinación y flujo de integración:** cada responsable desarrolla y mantiene su
  módulo siguiendo el flujo de ramas acordado por el equipo (ramas de funcionalidad
  integradas mediante revisión en la rama principal), asegurando que ninguna clave
  ni credencial quede registrada en el historial del repositorio.
