package es.uma.seguridad.labaccess.datos

import android.util.Log
import es.uma.seguridad.labaccess.modelo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/* ==========================================================================
   EL REPOSITORIO
   Teoría: sesión 2.5.4 (arquitectura por capas) y 2.4.5 (corrutinas).

   Es la ÚNICA puerta de acceso a los datos. La interfaz no sabe si la
   información viene de la red o de la base de datos local, y no debe saberlo.
   Cuando en el Bloque III se añada cifrado o anclaje de certificados se hará
   aquí y en el cliente de red, nunca en las pantallas.

   Fíjate en que todo el trabajo va dentro de withContext(Dispatchers.IO): es
   la regla de la sesión 2.4.5, nada de red ni de disco en el hilo principal.
   ========================================================================== */

class Repositorio(
    private val api: ApiServicio,
    private val sesion: SesionLocal,
    /*
     * [P3.1] Antes se recibía el DAO ya construido. Ahora la base de datos
     * está cifrada con una contraseña que sólo existe tras desbloquear la
     * sesión con la huella, así que se pide cuando hace falta. Devuelve null
     * si la sesión no está abierta.
     */
    private val proveedorDao: () -> RegistroAccesoDao?
) {

    /**
     * Inicia sesión contra el backend y guarda el token si todo va bien.
     *
     * Devuelve un EstadoUi en lugar de lanzar excepciones: el fallo es un
     * estado más del flujo normal, como se vio en la sesión 2.4.5.
     */
    suspend fun iniciarSesion(usuario: String, clave: String): EstadoUi<RespuestaLogin> =
        withContext(Dispatchers.IO) {
            try {
                val respuesta = api.login(PeticionLogin(usuario, clave))

                /* ----------------------------------------------------------
                   EL REGISTRO DELIBERADO

                   Esta línea es el ejemplo de la sesión 2.4.3 puesto en una
                   aplicación de verdad. RespuestaLogin es una clase de datos,
                   así que su toString() lo genera el compilador e incluye
                   TODOS los campos... incluido el token.

                   Nadie ha escrito «imprime el token»: han escrito «imprime
                   el objeto». El resultado es el mismo.

                   En la sección 5.1 lo verás en logcat. Después prueba a
                   comentar esta línea, vuelve a ejecutar y comprueba que el
                   token desaparece: así demuestras la causa en lugar de
                   suponerla.
                   ---------------------------------------------------------- */
                // [P3.1 · paso 2a] Antes: Log.d("Repositorio", "Login correcto: $respuesta"),
                // que volcaba el token entero vía el toString() de la data class.
                Log.d("Repositorio", "Login correcto")

                sesion.guardarSesion(respuesta.token, respuesta.usuario)
                EstadoUi.Exito(respuesta)

            } catch (e: HttpException) {
                /*
                 * El servidor ha respondido, pero con un código de error.
                 * 401 es contraseña incorrecta y 404 usuario inexistente: los
                 * tratamos IGUAL a propósito, para no confirmar qué cuentas
                 * existen. Es la idea de la sesión 2.4.5 sobre los mensajes
                 * que cuentan de más.
                 */
                when (e.code()) {
                    401, 404 -> EstadoUi.Error(MotivoError.CredencialesNoValidas)
                    else -> EstadoUi.Error(MotivoError.Desconocido)
                }
            } catch (e: IOException) {
                // No ha habido respuesta: red caída, backend apagado, dirección mal puesta
                EstadoUi.Error(MotivoError.SinConexion)
            } catch (e: Exception) {
                /*
                 * Cualquier otra cosa. Fíjate en lo que NO se hace: no se
                 * devuelve e.message a la interfaz. Esos mensajes llevan rutas,
                 * consultas y direcciones de servidor.
                 *
                 * [P3.1 · paso 4a] Pero tampoco se calla: un catch silencioso
                 * escondía los fallos del almacén de claves (tercera
                 * prohibición de la diapositiva 21). Se registra el TIPO de
                 * fallo y su causa, nunca el contenido ni el mensaje.
                 */
                Log.w(
                    "Repositorio",
                    "Fallo al iniciar sesión: ${e.javaClass.simpleName}" +
                        " / causa: ${e.cause?.javaClass?.simpleName}"
                )
                EstadoUi.Error(MotivoError.Desconocido)
            }
        }

    /**
     * Descarga los registros de acceso del vigilante, guarda una copia local y los devuelve.
     * Si la red falla, devuelve la copia local en lugar de un error.
     */
    suspend fun obtenerRegistros(): EstadoUi<List<RegistroAcceso>> =
        withContext(Dispatchers.IO) {

            /*
             * El operador ?: de la sesión 2.4.2 en su uso más típico: si no hay
             * token se sale de la función con un estado de error. No hay !!, no
             * hay comprobación que se pueda olvidar, y el compilador no deja
             * continuar con un valor que podría faltar.
             */
            val token = sesion.obtenerToken()
                ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
            val vigilante = sesion.obtenerUsuario() ?: ""

            // [P3.1] La base de datos cifrada sólo existe con la sesión abierta
            val dao = proveedorDao()

            try {
                val respuesta = api.registros("Bearer $token")

                // Copia local (cifrada), marcada con su vigilante
                dao?.guardar(
                    respuesta.registros.map { registro ->
                        RegistroAccesoEntidad(
                            id = registro.id,
                            persona = registro.persona,
                            detalle = registro.detalle,
                            fecha = registro.fecha,
                            vigilante = vigilante
                        )
                    }
                )
                EstadoUi.Exito(respuesta.registros)

            } catch (e: Exception) {
                /*
                 * Si la red falla recurrimos a la copia local. Éste es el
                 * motivo por el que existe la base de datos. [P3.1] Desde P3.1
                 * esa copia está cifrada con SQLCipher y se borra al cerrar
                 * sesión. La lectura va protegida: si la sesión se cierra a
                 * mitad, la base de datos ya no existe y no debe tumbar la app.
                 */
                val copiaLocal = try {
                    dao?.obtenerDe(vigilante).orEmpty()
                } catch (e: Exception) {
                    Log.w("Repositorio", "Copia local no disponible: ${e.javaClass.simpleName}")
                    emptyList()
                }
                if (copiaLocal.isEmpty()) {
                    EstadoUi.Error(MotivoError.SinConexion)
                } else {
                    EstadoUi.Exito(
                        copiaLocal.map { fila ->
                            RegistroAcceso(fila.id, fila.persona, fila.detalle, fila.fecha)
                        }
                    )
                }
            }
        }

    suspend fun obtenerPerfil(): EstadoUi<Perfil> = withContext(Dispatchers.IO) {
        val token = sesion.obtenerToken()
            ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
        try {
            EstadoUi.Exito(api.perfil("Bearer $token"))
        } catch (e: Exception) {
            EstadoUi.Error(MotivoError.Desconocido)
        }
    }

    suspend fun obtenerRegistro(id: Int): EstadoUi<RegistroAcceso> =
        withContext(Dispatchers.IO) {
            val token = sesion.obtenerToken()
                ?: return@withContext EstadoUi.Error(MotivoError.SesionCaducada)
            try {
                EstadoUi.Exito(api.registro("Bearer $token", id))
            } catch (e: IOException) {
                EstadoUi.Error(MotivoError.SinConexion)
            } catch (e: Exception) {
                EstadoUi.Error(MotivoError.Desconocido)
            }
        }

    /**
     * Cierra la sesión.
     *
     * [P3.1] SesionLocal.cerrarSesion() borra ahora TODO lo del vigilante en el
     * dispositivo: secretos en memoria, preferencias y el fichero de la base de
     * datos cifrada con su -wal y su -shm. Antes sólo se vaciaban las
     * preferencias y los registros se quedaban en disco, en claro, para el
     * siguiente vigilante del relevo.
     */
    suspend fun cerrarSesion() {
        withContext(Dispatchers.IO) {
            sesion.cerrarSesion()
        }
    }
}
