package es.uma.seguridad.labaccess.datos

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/* ==========================================================================
   LA CLAVE Y LAS OPERACIONES
   Teoría: sesión 3.3, diapositivas 6, 9, 12, 20 y 21. Práctica P3.1.
   Responsable: Ignacio Ferrer Téllez (almacenamiento local de datos sensibles).

   Este fichero es TODO lo que LabAccess sabe de criptografía. En ninguna
   línea aparece el material de una clave: hay un alias, unos propósitos, un
   modo, un tamaño y unas condiciones de uso. La aplicación le pide al
   Android Keystore que cree la clave y recibe una referencia; el material no
   sale nunca del almacén del sistema (TEE/StrongBox cuando existe).

   Partimos del Cripto.kt del material de P3.1, ya con las condiciones del
   paso 4, y le añadimos una única función propia de LabAccess:
   generarClaveBaseDatos(), para la contraseña aleatoria de SQLCipher.
   ========================================================================== */

object Cripto {

    /** El nombre con el que la aplicación se refiere a la clave. No es la clave. */
    const val ALIAS = "labaccess_clave_datos"

    private const val ALMACEN = "AndroidKeyStore"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"

    /** Etiqueta de autenticación de GCM completa: detecta texto cifrado manipulado. */
    private const val BITS_ETIQUETA = 128

    /** Separa el nonce del texto cifrado dentro de la misma cadena guardada. */
    private const val SEPARADOR = ":"

    /** Tamaño de la contraseña aleatoria de la base de datos (256 bits). */
    private const val BYTES_CLAVE_BD = 32

    /* ----------------------------------------------------------------------
       LA CLAVE
       ---------------------------------------------------------------------- */

    /**
     * Devuelve la clave, creándola la primera vez.
     *
     * Condiciones (paso 4 de P3.1):
     *  - setUserAuthenticationRequired(true): cada operación exige pasar antes
     *    por el diálogo biométrico. Lo impone keystore2, no este código.
     *  - setInvalidatedByBiometricEnrollment(true): si alguien registra una
     *    huella nueva en el terminal (p. ej. quien lo encontró desbloqueado
     *    en la garita), el sistema destruye la clave.
     *
     * Las condiciones se fijan AL CREAR la clave: cambiar este código no
     * modifica una clave que ya exista en el almacén.
     */
    fun obtenerOCrearClave(): SecretKey {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }

        val existente = almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existente != null) return existente.secretKey

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return generador.generateKey()
    }

    /** Destruye la clave: todo lo cifrado con ella queda irrecuperable. */
    fun borrarClave() {
        KeyStore.getInstance(ALMACEN).apply { load(null) }.deleteEntry(ALIAS)
    }

    /* ----------------------------------------------------------------------
       LOS CIFRADORES

       No se usan donde se crean: se entregan al diálogo biométrico como
       CryptoObject y sólo vuelven utilizables si la autenticación es correcta.
       ---------------------------------------------------------------------- */

    /**
     * Un cifrador listo para cifrar. El sistema le asigna un nonce nuevo en init().
     *
     * [LabAccess] La clave sobrevive al cierre de sesión. Si mientras no había
     * sesión guardada se registró una huella nueva, la clave que queda en el
     * almacén está invalidada y init() lanza KeyPermanentlyInvalidatedException.
     * Al CIFRAR no hay nada que perder (se va a guardar una sesión nueva), así
     * que se destruye y se crea otra. Al descifrar no se hace esto: allí la
     * invalidación significa que la sesión guardada es irrecuperable.
     */
    fun cifradorParaCifrar(): Cipher =
        try {
            nuevoCifradorParaCifrar()
        } catch (e: KeyPermanentlyInvalidatedException) {
            borrarClave()
            nuevoCifradorParaCifrar()
        }

    private fun nuevoCifradorParaCifrar(): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
        }

    /** Un cifrador listo para descifrar ese paquete concreto (necesita su nonce). */
    fun cifradorParaDescifrar(paquete: String): Cipher {
        val nonce = Base64.decode(paquete.substringBefore(SEPARADOR), Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, obtenerOCrearClave(), GCMParameterSpec(BITS_ETIQUETA, nonce))
        }
    }

    /* ----------------------------------------------------------------------
       LAS OPERACIONES
       ---------------------------------------------------------------------- */

    /**
     * Cifra y empaqueta en una sola cadena: base64(nonce) ":" base64(cifrado).
     *
     * El nonce no es secreto, pero es imprescindible para descifrar: por eso
     * viaja dentro del paquete. Nunca se fija ni se reutiliza (diapositiva 12).
     */
    fun empaquetar(cifrador: Cipher, texto: String): String {
        val cifrado = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrador.iv, Base64.NO_WRAP) +
            SEPARADOR +
            Base64.encodeToString(cifrado, Base64.NO_WRAP)
    }

    /**
     * Deshace lo anterior. Si el paquete se ha modificado, doFinal() lanza
     * AEADBadTagException: es el modo autenticado haciendo su trabajo.
     */
    fun desempaquetar(cifrador: Cipher, paquete: String): String {
        val cifrado = Base64.decode(paquete.substringAfter(SEPARADOR), Base64.NO_WRAP)
        return String(cifrador.doFinal(cifrado), Charsets.UTF_8)
    }

    /* ----------------------------------------------------------------------
       PROPIO DE LABACCESS: LA CONTRASEÑA DE LA BASE DE DATOS
       ---------------------------------------------------------------------- */

    /**
     * 32 bytes del generador criptográfico del sistema (diapositiva 21).
     *
     * No se deriva de nada ni se escribe en claro en ningún sitio: viaja
     * dentro del mismo paquete cifrado que el token, de modo que una sola
     * huella abre la sesión y la base de datos a la vez.
     */
    fun generarClaveBaseDatos(): ByteArray =
        ByteArray(BYTES_CLAVE_BD).also { SecureRandom().nextBytes(it) }
}
