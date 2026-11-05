package es.uma.seguridad.labaccess.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.uma.seguridad.labaccess.modelo.*

/**
 * Listado de registros de acceso. ESTÁ TERMINADA: no hace falta tocarla en P2.2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(
    estado: EstadoUi<List<RegistroAcceso>>,
    onRecargar: () -> Unit,
    onSalir: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registros de acceso") },
                actions = {
                    TextButton(onClick = onRecargar) { Text("Recargar") }
                    TextButton(onClick = onSalir) { Text("Salir") }
                }
            )
        }
    ) { relleno ->
        Box(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentAlignment = Alignment.Center
        ) {
            when (estado) {
                is EstadoUi.Reposo -> Text("Pulsa Recargar para cargar los datos")
                is EstadoUi.Cargando -> CircularProgressIndicator()
                is EstadoUi.Error -> Text("No se han podido cargar los registros")
                is EstadoUi.Exito -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    items(estado.datos) { registro ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(registro.persona, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(registro.detalle, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(registro.fecha, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
