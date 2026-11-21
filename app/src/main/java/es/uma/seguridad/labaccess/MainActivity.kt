package es.uma.seguridad.labaccess

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import es.uma.seguridad.labaccess.datos.*
import es.uma.seguridad.labaccess.ui.Navegacion
import es.uma.seguridad.labaccess.ui.PuertaBiometrica
import es.uma.seguridad.labaccess.ui.theme.LabAccessTheme

/* ==========================================================================
   PUNTO DE ENTRADA · versión de P3.1 (paso 4)
   Responsable del cambio: Ignacio Ferrer Téllez (almacenamiento local).

   Parte de paso4/MainActivity.kt del material. Cambios respecto a P2.3:

   1. ComponentActivity -> FragmentActivity. Lo exige BiometricPrompt, que se
      apoya en el gestor de fragmentos para sobrevivir a un giro de pantalla
      con el dedo en el sensor.

   2. Una sola instancia de SesionLocal, compartida por el repositorio y la
      puerta biométrica. [LabAccess] Se obtiene con SesionLocal.obtener(), que
      es única por PROCESO y no por actividad: el VistaModelo sobrevive a los
      giros de pantalla con el repositorio antiguo, y si cada onCreate creara
      su propia sesión, el repositorio y la puerta verían tokens distintos.

   3. La navegación va envuelta en la puerta: mientras la sesión guardada esté
      bloqueada, la interfaz con datos no llega a componerse.

   4. [LabAccess] La base de datos ya no se abre aquí. Está cifrada con una
      contraseña que sólo existe tras la huella, así que el repositorio la
      abre bajo demanda a través de proveedorDao.
   ========================================================================== */

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sesion = SesionLocal.obtener(applicationContext)

        // Se captura el contexto de la APLICACIÓN, no la actividad: el
        // VistaModelo guarda este repositorio más allá de un giro de pantalla.
        val contextoApp = applicationContext

        val repositorio = Repositorio(
            api = ClienteRed.crear(),
            sesion = sesion,
            proveedorDao = {
                sesion.obtenerClaveBaseDatos()?.let { contrasena ->
                    BaseDatos.abrir(contextoApp, contrasena).registroAccesoDao()
                }
            }
        )

        setContent {
            LabAccessTheme {
                val vistaModelo: VistaModelo = viewModel(factory = FabricaVistaModelo(repositorio))

                PuertaBiometrica(actividad = this@MainActivity, sesion = sesion) {
                    /*
                     * [LabAccess] Tras desbloquear con la huella la sesión ya está
                     * abierta en memoria: se entra directamente al listado en vez
                     * de volver a pedir usuario y contraseña.
                     */
                    Navegacion(vistaModelo, inicio = if (sesion.haySesion()) "lista" else "login")
                }
            }
        }
    }
}

class FabricaVistaModelo(private val repositorio: Repositorio) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VistaModelo(repositorio) as T
}
