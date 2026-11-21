package es.uma.seguridad.labaccess.datos

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher

/* ==========================================================================
   LA SESIÓN DEL VIGILANTE · versión cifrada y ligada a la biometría
   Teoría: sesión 3.3, diapositivas 19, 20 y 21. Práctica P3.1, paso 4.
   Responsable: Ignacio Ferrer Téllez.

   Parte de paso4/SesionLocal.kt del material de P3.1. Cambios propios de
   LabAccess, marcados con [LabAccess] en el código:

   1. El paquete cifrado guarda TRES valores: token, usuario y la contraseña
      aleatoria de la base de datos (SQLCipher). Una huella lo abre todo.
   2. La base de datos vive lo mismo que la sesión: al cerrar sesión, al
      invalidarse la clave o al encontrar una base de datos huérfana, se
      borra el fichero entero (con -wal y -shm). Protege el relevo de turno.
   3. Limpieza de lo que dejó la versión de P2.2: los campos "token" y
      "usuario" en claro y el labaccess.db sin cifrar. Cifrar en la versión
      N+1 no protege lo que escribió la versión N si nadie lo borra.
   4. Una única instancia por proceso (obtener()). Con dos instancias, el
      VistaModelo (que sobrevive a un giro de pantalla) y la puerta
      biométrica tendrían cada uno su copia del token en memoria.

   Lo que NO protege: mientras la sesión está ABIERTA, el token y la
   contraseña de la base de datos están en la memoria del proceso. El
   cifrado protege el disco, no el proceso en ejecución.
   ========================================================================== */

class SesionLocal private constructor(contexto: Context) {

    private val contextoApp: Context = contexto.applicationContext

    private val preferencias: SharedPreferences =
        contextoApp.getSharedPreferences(NOMBRE_FICHERO, Context.MODE_PRIVATE)

    /** En qué situación está la sesión. La puerta biométrica la observa. */
    enum class Estado {
        /** No hay nada guardado: hay que iniciar sesión. */
        SIN_SESION,

        /** Se acaba de iniciar sesión y falta confirmar con la huella para guardarla cifrada. */
        PENDIENTE_DE_GUARDAR,

        /** Hay sesión guardada pero todavía no se ha desbloqueado. */
        BLOQUEADA,

        /** Desbloqueada: los secretos están en memoria y la aplicación puede trabajar. */
        ABIERTA
    }

    @Volatile private var tokenEnMemoria: String? = null
    @Volatile private var usuarioEnMemoria: String? = null
    @Volatile private var claveBdEnMemoria: ByteArray? = null

    private val _estado: MutableStateFlow<Estado>
    val estado: StateFlow<Estado>

    init {
        limpiarRestosDeVersionesAnteriores()
        _estado = MutableStateFlow(
            if (preferencias.contains(CLAVE_DATOS)) Estado.BLOQUEADA else Estado.SIN_SESION
        )
        estado = _estado.asStateFlow()
    }

    /**
     * [LabAccess] Borra lo que quedó en claro de versiones anteriores y la base
     * de datos huérfana.
     *
     * - Campos "token" y "usuario" de P2.2: en claro, legibles con run-as.
     * - Si no hay sesión guardada, cualquier labaccess.db que exista es o bien
     *   el de P2.2 (en claro) o bien uno cifrado con una contraseña que ya no
     *   tenemos (sesión que nunca se llegó a proteger). En ambos casos sobra.
     */
    private fun limpiarRestosDeVersionesAnteriores() {
        if (preferencias.contains(CLAVE_HEREDADA_TOKEN) || preferencias.contains(CLAVE_HEREDADA_USUARIO)) {
            preferencias.edit()
                .remove(CLAVE_HEREDADA_TOKEN)
                .remove(CLAVE_HEREDADA_USUARIO)
                .commit()
            Log.i(ETIQUETA, "Eliminados campos de sesión en claro de una versión anterior")
        }
        if (!preferencias.contains(CLAVE_DATOS)) {
            BaseDatos.cerrarYBorrar(contextoApp)
        }
    }

    /* ----------------------------------------------------------------------
       LO QUE VE EL RESTO DE LA APLICACIÓN (misma firma que en P2.2)
       ---------------------------------------------------------------------- */

    /**
     * Guarda la sesión... en memoria, y marca que falta persistirla cifrada.
     *
     * No puede cifrar aquí: la clave exige autenticación y esta función se
     * llama desde una corrutina del repositorio, sin pantalla. La puerta
     * biométrica verá PENDIENTE_DE_GUARDAR y pedirá la huella.
     *
     * [LabAccess] Cada inicio de sesión estrena base de datos y contraseña: la
     * del vigilante anterior se destruye antes.
     */
    fun guardarSesion(token: String, usuario: String) {
        BaseDatos.cerrarYBorrar(contextoApp)
        olvidarClaveBd()
        tokenEnMemoria = token
        usuarioEnMemoria = usuario
        claveBdEnMemoria = Cripto.generarClaveBaseDatos()
        _estado.value = Estado.PENDIENTE_DE_GUARDAR
    }

    fun obtenerToken(): String? = tokenEnMemoria

    fun obtenerUsuario(): String? = usuarioEnMemoria

    fun haySesion(): Boolean = tokenEnMemoria != null

    /**
     * [LabAccess] La contraseña de la base de datos, o null si la sesión no está
     * abierta. Se devuelve una COPIA: SQLCipher borra el array que recibe.
     */
    fun obtenerClaveBaseDatos(): ByteArray? = claveBdEnMemoria?.copyOf()

    /**
     * Cierra la sesión y borra TODO lo del vigilante en el dispositivo:
     * secretos en memoria, preferencias y base de datos (con -wal y -shm).
     * La clave del almacén se queda: no se ha invalidado nada.
     */
    fun cerrarSesion() {
        olvidarSecretos()
        preferencias.edit().clear().commit()
        BaseDatos.cerrarYBorrar(contextoApp)
        _estado.value = Estado.SIN_SESION
    }

    /* ----------------------------------------------------------------------
       LO QUE USA LA PUERTA BIOMÉTRICA

       El cifrador se crea aquí, se entrega al diálogo del sistema y sólo
       vuelve utilizable si la autenticación salió bien.
       ---------------------------------------------------------------------- */

    /** Cifrador para persistir la sesión recién iniciada. Va al diálogo. */
    fun cifradorParaGuardar(): Cipher = Cripto.cifradorParaCifrar()

    /** Cifrador para abrir la sesión guardada. Va al diálogo. */
    fun cifradorParaAbrir(): Cipher {
        val paquete = preferencias.getString(CLAVE_DATOS, null)
            ?: throw IllegalStateException("No hay sesión guardada")
        return Cripto.cifradorParaDescifrar(paquete)
    }

    /** Termina de guardar con el cifrador que devuelve el diálogo (el único habilitado). */
    fun completarGuardado(cifrador: Cipher) {
        val token = tokenEnMemoria ?: return
        val usuario = usuarioEnMemoria ?: ""
        val claveBd = claveBdEnMemoria ?: return
        val claro = listOf(token, usuario, Base64.encodeToString(claveBd, Base64.NO_WRAP))
            .joinToString(SEPARADOR_CAMPOS)
        val paquete = Cripto.empaquetar(cifrador, claro)
        preferencias.edit().putString(CLAVE_DATOS, paquete).commit()
        _estado.value = Estado.ABIERTA
    }

    /**
     * Termina de abrir con el cifrador que devuelve el diálogo.
     *
     * Devuelve false si los datos ya no se pueden recuperar y, en ese caso,
     * los descarta: no hay nada que reintentar.
     */
    fun completarApertura(cifrador: Cipher): Boolean {
        val paquete = preferencias.getString(CLAVE_DATOS, null) ?: return false
        return try {
            val campos = Cripto.desempaquetar(cifrador, paquete).split(SEPARADOR_CAMPOS)
            require(campos.size == 3) { "Formato de sesión no reconocido" }
            tokenEnMemoria = campos[0]
            usuarioEnMemoria = campos[1]
            claveBdEnMemoria = Base64.decode(campos[2], Base64.NO_WRAP)
            _estado.value = Estado.ABIERTA
            true
        } catch (e: Exception) {
            // Se registra QUE ha fallado y de qué tipo. Nunca el contenido.
            Log.w(ETIQUETA, "Sesión irrecuperable: ${e.javaClass.simpleName}")
            descartarSesionIrrecuperable()
            false
        }
    }

    /**
     * Cuando la clave ya no sirve (huella nueva, bloqueo de pantalla retirado,
     * paquete manipulado). Se borra todo: datos cifrados, base de datos y la
     * propia clave, que puede estar invalidada.
     */
    fun descartarSesionIrrecuperable() {
        olvidarSecretos()
        preferencias.edit().clear().commit()
        BaseDatos.cerrarYBorrar(contextoApp)
        try {
            Cripto.borrarClave()
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se ha podido borrar la clave: ${e.javaClass.simpleName}")
        }
        _estado.value = Estado.SIN_SESION
    }

    /* ---------------------------------------------------------------------- */

    private fun olvidarSecretos() {
        tokenEnMemoria = null
        usuarioEnMemoria = null
        olvidarClaveBd()
    }

    /** Sobrescribe la contraseña en memoria antes de soltarla (mejor esfuerzo). */
    private fun olvidarClaveBd() {
        claveBdEnMemoria?.fill(0)
        claveBdEnMemoria = null
    }

    companion object {
        private const val ETIQUETA = "SesionLocal"

        const val NOMBRE_FICHERO = "sesion"

        /** Un único campo: una operación criptográfica, una sola huella. */
        const val CLAVE_DATOS = "datos"

        /** Campos en claro que escribía la versión de P2.2. Sólo para borrarlos. */
        private const val CLAVE_HEREDADA_TOKEN = "token"
        private const val CLAVE_HEREDADA_USUARIO = "usuario"

        /** Separa los campos dentro del paquete cifrado. */
        private const val SEPARADOR_CAMPOS = "\u0000"

        @Volatile private var instancia: SesionLocal? = null

        /** [LabAccess] Una sola instancia por proceso, compartida por repositorio y puerta. */
        fun obtener(contexto: Context): SesionLocal =
            instancia ?: synchronized(this) {
                instancia ?: SesionLocal(contexto).also { instancia = it }
            }
    }
}
