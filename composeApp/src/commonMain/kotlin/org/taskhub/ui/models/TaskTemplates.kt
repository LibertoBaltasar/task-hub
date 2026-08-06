package org.taskhub.ui.models

/**
 * Plantillas de tareas predefinidas para relleno rápido del formulario.
 */
data class TaskTemplate(
    val title: String,
    val tags: List<String>,
    val frequency: String,
    val points: Int,
    val description: String = "",
    val category: TemplateCategory
)

enum class TemplateCategory(val emoji: String, val label: String) {
    LIMPIEZA("\uD83E\uDDF9", "Limpieza"),
    COCINA("\uD83C\uDF73", "Cocina"),
    NINOS("\uD83D\uDC76", "Niños"),
    GENERAL("\uD83C\uDFE0", "General")
}

object TaskTemplates {

    val all: List<TaskTemplate> = listOf(
        // ── Limpieza ──
        TaskTemplate(
            title = "Lavar platos",
            tags = listOf("limpieza", "cocina"),
            frequency = "daily",
            points = 10,
            description = "Lavar y secar todos los platos del día",
            category = TemplateCategory.LIMPIEZA
        ),
        TaskTemplate(
            title = "Aspirar",
            tags = listOf("limpieza"),
            frequency = "weekly",
            points = 25,
            description = "Aspirar todas las habitaciones de la casa",
            category = TemplateCategory.LIMPIEZA
        ),
        TaskTemplate(
            title = "Limpiar baño",
            tags = listOf("limpieza", "baño"),
            frequency = "weekly",
            points = 30,
            description = "Limpiar lavabo, inodoro, ducha y espejos",
            category = TemplateCategory.LIMPIEZA
        ),
        TaskTemplate(
            title = "Sacar basura",
            tags = listOf("limpieza"),
            frequency = "daily",
            points = 10,
            description = "Sacar la basura al contenedor y poner bolsa nueva",
            category = TemplateCategory.LIMPIEZA
        ),
        TaskTemplate(
            title = "Quitar polvo",
            tags = listOf("limpieza"),
            frequency = "weekly",
            points = 15,
            description = "Quitar el polvo de muebles, estanterías y superficies",
            category = TemplateCategory.LIMPIEZA
        ),

        // ── Cocina ──
        TaskTemplate(
            title = "Preparar cena",
            tags = listOf("cocina"),
            frequency = "daily",
            points = 20,
            description = "Preparar la cena para toda la familia",
            category = TemplateCategory.COCINA
        ),
        TaskTemplate(
            title = "Hacer la compra",
            tags = listOf("cocina", "compras"),
            frequency = "weekly",
            points = 30,
            description = "Hacer la compra semanal en el supermercado",
            category = TemplateCategory.COCINA
        ),
        TaskTemplate(
            title = "Poner lavavajillas",
            tags = listOf("cocina", "limpieza"),
            frequency = "daily",
            points = 10,
            description = "Cargar y poner el lavavajillas después de comer",
            category = TemplateCategory.COCINA
        ),
        TaskTemplate(
            title = "Recoger cocina",
            tags = listOf("cocina", "limpieza"),
            frequency = "daily",
            points = 10,
            description = "Recoger y limpiar encimeras, fregadero y fogones",
            category = TemplateCategory.COCINA
        ),

        // ── Niños ──
        TaskTemplate(
            title = "Recoger habitación",
            tags = listOf("niños", "limpieza"),
            frequency = "daily",
            points = 15,
            description = "Recoger juguetes y ordenar la habitación",
            category = TemplateCategory.NINOS
        ),
        TaskTemplate(
            title = "Hacer deberes",
            tags = listOf("niños"),
            frequency = "daily",
            points = 20,
            description = "Hacer los deberes del colegio sin distracciones",
            category = TemplateCategory.NINOS
        ),
        TaskTemplate(
            title = "Bañar a los niños",
            tags = listOf("niños"),
            frequency = "daily",
            points = 15,
            description = "Bañar a los peques antes de cenar",
            category = TemplateCategory.NINOS
        ),

        // ── General ──
        TaskTemplate(
            title = "Regar plantas",
            tags = listOf("exterior", "plantas"),
            frequency = "weekly",
            points = 10,
            description = "Regar todas las plantas de interior y exterior",
            category = TemplateCategory.GENERAL
        ),
        TaskTemplate(
            title = "Pasear al perro",
            tags = listOf("mascotas"),
            frequency = "daily",
            points = 15,
            description = "Sacar al perro a pasear al menos 30 minutos",
            category = TemplateCategory.GENERAL
        ),
        TaskTemplate(
            title = "Poner lavadora",
            tags = listOf("limpieza"),
            frequency = "weekly",
            points = 20,
            description = "Poner una lavadora, tender y recoger la ropa",
            category = TemplateCategory.GENERAL
        ),
        TaskTemplate(
            title = "Tender ropa",
            tags = listOf("limpieza"),
            frequency = "weekly",
            points = 15,
            description = "Tender la ropa limpia y recogerla cuando esté seca",
            category = TemplateCategory.GENERAL
        )
    )

    /** Templates agrupados por categoría */
    val byCategory: Map<TemplateCategory, List<TaskTemplate>> = all.groupBy { it.category }
}