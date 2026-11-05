package es.uma.seguridad.labaccess.datos

import android.content.Context
import androidx.room.*

/* ==========================================================================
   LA BASE DE DATOS LOCAL
   Teoría: sesión 2.2.5 y 2.5.5.

   La aplicación guarda una copia local de los registros de acceso descargados
   para poder mostrarlos cuando no hay conexión: es lo que permite al vigilante
   seguir consultando el histórico si cae la red. Room es una capa sobre SQLite que escribe
   el SQL por ti y comprueba las consultas al compilar.

   AVISO: el fichero de base de datos se escribe SIN CIFRAR. No es un descuido,
   es el comportamiento por defecto de Room. En la sección 5.3 lo sacarás del
   dispositivo y lo abrirás; en la práctica P3.1 lo cifrarás.
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

        fun obtener(contexto: Context): BaseDatos =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    contexto.applicationContext,
                    BaseDatos::class.java,
                    NOMBRE_FICHERO
                ).build().also { instancia = it }
            }

        /** El fichero acaba en /data/data/<tu.paquete>/databases/labaccess.db */
        const val NOMBRE_FICHERO = "labaccess.db"
    }
}
