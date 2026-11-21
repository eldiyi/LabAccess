package es.uma.seguridad.labaccess.datos

import android.content.Context
import androidx.room.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/* ==========================================================================
   LA BASE DE DATOS LOCAL
   Teoría: sesión 2.2.5 y 2.5.5.

   La aplicación guarda una copia local de los registros de acceso descargados
   para poder mostrarlos cuando no hay conexión: es lo que permite al vigilante
   seguir consultando el histórico si cae la red. Room es una capa sobre SQLite que escribe
   el SQL por ti y comprueba las consultas al compilar.

   P3.1 (Ignacio Ferrer Téllez): desde esta versión el fichero se escribe
   CIFRADO con SQLCipher (AES-256 por página). Room no cifra por sí mismo;
   SQLCipher sustituye a la capa SQLite que hay debajo. Ver abrir() más abajo.
   ========================================================================== */

/**
 * Una fila de la tabla.
 *
 * Fíjate en el campo `vigilante`, que no viene del backend: lo añadimos
 * nosotros para saber de qué usuario son los datos. Sin él, al entrar con otra
 * cuenta en el mismo terminal (relevo de turno) se verían mezclados los registros
 * de los dos vigilantes.
 */
@Entity(tableName = "registros_acceso")
data class RegistroAccesoEntidad(
    @PrimaryKey val id: Int,
    val persona: String,
    val detalle: String,
    val fecha: String,
    val vigilante: String
)

/**
 * El DAO declara QUÉ consultas existen; Room genera el código que las ejecuta
 * y comprueba el SQL al compilar, no en ejecución.
 *
 * Las tres funciones son `suspend`: no se pueden llamar desde el hilo
 * principal y el compilador lo impone. Es la regla de la sesión 2.4.5
 * convertida en una restricción del lenguaje.
 */
@Dao
interface RegistroAccesoDao {

    /** Los registros de un vigilante, del más reciente al más antiguo. */
    @Query("SELECT * FROM registros_acceso WHERE vigilante = :vigilante ORDER BY fecha DESC")
    suspend fun obtenerDe(vigilante: String): List<RegistroAccesoEntidad>

    /** Inserta una lista; si ya existe una fila con ese id, la reemplaza. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(registros: List<RegistroAccesoEntidad>)

    /** Borra todo lo de un vigilante. Existe; otra cosa es que alguien la llame. */
    @Query("DELETE FROM registros_acceso WHERE vigilante = :vigilante")
    suspend fun borrarDe(vigilante: String)
}

@Database(entities = [RegistroAccesoEntidad::class], version = 1, exportSchema = false)
abstract class BaseDatos : RoomDatabase() {

    abstract fun registroAccesoDao(): RegistroAccesoDao

    companion object {

        /**
         * Abrir dos veces la misma base de datos corrompe los datos, así que se
         * guarda una única instancia. @Volatile y synchronized evitan que dos
         * hilos la construyan a la vez.
         */
        @Volatile private var instancia: BaseDatos? = null

        /*
         * [P3.1 · LabAccess] La base de datos se abre CIFRADA con SQLCipher.
         *
         * La contraseña (32 bytes aleatorios) no está en ningún fichero en
         * claro: viaja dentro del paquete de la sesión, cifrado con la clave
         * del Android Keystore que exige huella. Por eso la base de datos sólo
         * puede abrirse DESPUÉS de desbloquear la sesión, y ya no se construye
         * al arrancar MainActivity sino la primera vez que el repositorio la
         * necesita.
         *
         * SupportOpenHelperFactory pone a cero el array que recibe tras abrir
         * la base de datos, de modo que la contraseña no se queda en esta
         * clase. Quien llama debe pasar una copia (SesionLocal ya lo hace).
         */
        fun abrir(contexto: Context, contrasena: ByteArray): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: run {
                    cargarSqlCipher()
                    Room.databaseBuilder(
                        contexto.applicationContext,
                        BaseDatos::class.java,
                        NOMBRE_FICHERO
                    )
                        .openHelperFactory(SupportOpenHelperFactory(contrasena))
                        .build()
                        .also { instancia = it }
                }
            }

        /**
         * [P3.1 · LabAccess] Cierra la base de datos y borra el fichero con su
         * -wal, su -shm y su -journal (deleteDatabase se ocupa de los cuatro).
         *
         * Se llama al cerrar sesión, al invalidarse la clave y al empezar una
         * sesión nueva: los registros de un vigilante no sobreviven a su turno.
         */
        fun cerrarYBorrar(contexto: Context) {
            synchronized(this) {
                instancia?.close()
                instancia = null
                contexto.applicationContext.deleteDatabase(NOMBRE_FICHERO)
            }
        }

        @Volatile private var sqlCipherCargado = false

        /** La biblioteca nativa de SQLCipher debe cargarse antes de abrir nada. */
        private fun cargarSqlCipher() {
            if (!sqlCipherCargado) {
                System.loadLibrary("sqlcipher")
                sqlCipherCargado = true
            }
        }

        /** El fichero acaba en /data/data/<tu.paquete>/databases/labaccess.db */
        const val NOMBRE_FICHERO = "labaccess.db"
    }
}
