package org.taskhub.storage

import com.russhwolf.settings.Settings
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [TaskCache] estaba en 0% de cobertura (panel de revisión 2026-09-03/04,
 * Experto 13, confirmado cubrible sin mocks nuevos, panel v7, #30) — usa el
 * mismo doble de prueba `FakeSettings` en memoria que [SettingsStoreTest],
 * duplicado aquí (es `private` en ese archivo) en vez de compartir una
 * dependencia de test nueva.
 */
class TaskCacheTest {

    private fun cache(settings: Settings = FakeCacheSettings()) = TaskCache(settings)

    private fun task(id: String = "t1", householdId: String = "h1", title: String = "Fregar") =
        TaskResponse(id = id, householdId = householdId, createdBy = "m1", title = title)

    private fun household(id: String = "h1", name: String = "Casa") =
        HouseholdResponse(id = id, name = name, inviteCode = "ABC123")

    private fun member(id: String = "m1", householdId: String = "h1", displayName: String = "Ana") =
        MemberResponse(id = id, householdId = householdId, displayName = displayName, role = "child")

    private fun assignment(id: String = "a1", taskId: String = "t1", memberId: String = "m1") =
        TaskAssignmentResponse(id = id, taskId = taskId, memberId = memberId)

    // ── Tasks ───────────────────────────────────────────────

    /** Sin ninguna caché previa, leer tareas de un hogar devuelve `null`. */
    @Test
    fun getCachedTasks_withoutCaching_returnsNull() {
        assertNull(cache().getCachedTasks("h1"))
    }

    /** Las tareas guardadas con [TaskCache.cacheTasks] se recuperan igual con [TaskCache.getCachedTasks]. */
    @Test
    fun cacheTasks_thenGetCachedTasks_returnsSameList() {
        val c = cache()
        val tasks = listOf(task(id = "t1"), task(id = "t2"))

        c.cacheTasks("h1", tasks)

        assertEquals(tasks, c.getCachedTasks("h1"))
    }

    /** La caché de tareas está aislada por hogar: cachear en uno no mezcla datos con otro. */
    @Test
    fun cacheTasks_isScopedPerHousehold() {
        val c = cache()
        c.cacheTasks("h1", listOf(task(id = "t1", householdId = "h1")))
        c.cacheTasks("h2", listOf(task(id = "t2", householdId = "h2")))

        assertEquals(listOf(task(id = "t1", householdId = "h1")), c.getCachedTasks("h1"))
        assertEquals(listOf(task(id = "t2", householdId = "h2")), c.getCachedTasks("h2"))
    }

    /** [TaskCache.clearTasks] borra solo la caché de tareas del hogar, sin tocar la del hogar en sí. */
    @Test
    fun clearTasks_removesOnlyTasksCache() {
        val c = cache()
        c.cacheTasks("h1", listOf(task()))
        c.cacheHousehold(household())

        c.clearTasks("h1")

        assertNull(c.getCachedTasks("h1"))
        assertEquals(household(), c.getCachedHousehold("h1")) // no afectado
    }

    /** Un JSON corrupto en el almacenamiento subyacente no rompe la lectura: devuelve `null` en vez de lanzar. */
    @Test
    fun getCachedTasks_withCorruptedJson_returnsNullInsteadOfThrowing() {
        val settings = FakeCacheSettings(mutableMapOf("cache_tasks_h1" to "{not-valid-json"))

        assertNull(cache(settings).getCachedTasks("h1"))
    }

    // ── Household ───────────────────────────────────────────

    /** Sin caché previa, leer el hogar devuelve `null`. */
    @Test
    fun getCachedHousehold_withoutCaching_returnsNull() {
        assertNull(cache().getCachedHousehold("h1"))
    }

    /** El hogar guardado con [TaskCache.cacheHousehold] se recupera igual con [TaskCache.getCachedHousehold]. */
    @Test
    fun cacheHousehold_thenGetCachedHousehold_returnsSameValue() {
        val c = cache()
        c.cacheHousehold(household())

        assertEquals(household(), c.getCachedHousehold("h1"))
    }

    /** [TaskCache.clearHouseholdDoc] borra solo la caché del documento de hogar, sin afectar a la de tareas. */
    @Test
    fun clearHouseholdDoc_removesOnlyHouseholdCache() {
        val c = cache()
        c.cacheHousehold(household())
        c.cacheTasks("h1", listOf(task()))

        c.clearHouseholdDoc("h1")

        assertNull(c.getCachedHousehold("h1"))
        assertTrue(c.getCachedTasks("h1")!!.isNotEmpty()) // no afectado
    }

    // ── Members ─────────────────────────────────────────────

    /** Sin caché previa, leer los miembros de un hogar devuelve `null`. */
    @Test
    fun getCachedMembers_withoutCaching_returnsNull() {
        assertNull(cache().getCachedMembers("h1"))
    }

    /** Los miembros guardados con [TaskCache.cacheMembers] se recuperan igual con [TaskCache.getCachedMembers]. */
    @Test
    fun cacheMembers_thenGetCachedMembers_returnsSameList() {
        val c = cache()
        val members = listOf(member(id = "m1"), member(id = "m2"))

        c.cacheMembers("h1", members)

        assertEquals(members, c.getCachedMembers("h1"))
    }

    /** [TaskCache.clearMembers] borra solo la caché de miembros, sin afectar a la del hogar. */
    @Test
    fun clearMembers_removesOnlyMembersCache() {
        val c = cache()
        c.cacheMembers("h1", listOf(member()))
        c.cacheHousehold(household())

        c.clearMembers("h1")

        assertNull(c.getCachedMembers("h1"))
        assertEquals(household(), c.getCachedHousehold("h1")) // no afectado
    }

    // ── Assignments ─────────────────────────────────────────
    // Añadidas al resolver el encargo de kanban "[Caché]
    // getAssignments/getAllAssignments sin caché offline".

    /** Sin caché previa, leer las asignaciones de una tarea devuelve `null`. */
    @Test
    fun getCachedAssignments_withoutCaching_returnsNull() {
        assertNull(cache().getCachedAssignments("h1", "t1"))
    }

    /** Las asignaciones guardadas con [TaskCache.cacheAssignments] se recuperan igual con [TaskCache.getCachedAssignments]. */
    @Test
    fun cacheAssignments_thenGetCachedAssignments_returnsSameList() {
        val c = cache()
        val assignments = listOf(assignment(id = "a1"), assignment(id = "a2"))

        c.cacheAssignments("h1", "t1", assignments)

        assertEquals(assignments, c.getCachedAssignments("h1", "t1"))
    }

    /** La caché de asignaciones está aislada por tarea: cachear en una no mezcla datos con otra del mismo hogar. */
    @Test
    fun cacheAssignments_isScopedPerTask() {
        val c = cache()
        c.cacheAssignments("h1", "t1", listOf(assignment(id = "a1", taskId = "t1")))
        c.cacheAssignments("h1", "t2", listOf(assignment(id = "a2", taskId = "t2")))

        assertEquals(listOf(assignment(id = "a1", taskId = "t1")), c.getCachedAssignments("h1", "t1"))
        assertEquals(listOf(assignment(id = "a2", taskId = "t2")), c.getCachedAssignments("h1", "t2"))
    }

    /** [TaskCache.clearAssignments] borra solo la caché de asignaciones de esa tarea, sin tocar la de tareas. */
    @Test
    fun clearAssignments_removesOnlyThatTasksAssignmentsCache() {
        val c = cache()
        c.cacheAssignments("h1", "t1", listOf(assignment()))
        c.cacheAssignments("h1", "t2", listOf(assignment(id = "a2", taskId = "t2")))
        c.cacheTasks("h1", listOf(task()))

        c.clearAssignments("h1", "t1")

        assertNull(c.getCachedAssignments("h1", "t1"))
        assertEquals(listOf(assignment(id = "a2", taskId = "t2")), c.getCachedAssignments("h1", "t2")) // no afectado
        assertTrue(c.getCachedTasks("h1")!!.isNotEmpty()) // no afectado
    }

    /** Un JSON corrupto en el almacenamiento subyacente no rompe la lectura: devuelve `null` en vez de lanzar. */
    @Test
    fun getCachedAssignments_withCorruptedJson_returnsNullInsteadOfThrowing() {
        val settings = FakeCacheSettings(mutableMapOf("cache_assignments_h1_t1" to "{not-valid-json"))

        assertNull(cache(settings).getCachedAssignments("h1", "t1"))
    }

    // ── clearHousehold — borra las 3 cachés a la vez ─────────

    /** [TaskCache.clearHousehold] borra de golpe las 3 cachés (tareas, hogar, miembros) de ese hogar. */
    @Test
    fun clearHousehold_removesTasksHouseholdAndMembersCaches() {
        val c = cache()
        c.cacheTasks("h1", listOf(task()))
        c.cacheHousehold(household())
        c.cacheMembers("h1", listOf(member()))

        c.clearHousehold("h1")

        assertNull(c.getCachedTasks("h1"))
        assertNull(c.getCachedHousehold("h1"))
        assertNull(c.getCachedMembers("h1"))
    }

    /**
     * [TaskCache.clearHousehold] también purga la caché de asignaciones de
     * cada tarea que estuviera cacheada (no hay índice propio de
     * asignaciones: se deriva de la caché de tareas, ver KDoc de la función).
     */
    @Test
    fun clearHousehold_alsoRemovesAssignmentsCacheOfCachedTasks() {
        val c = cache()
        c.cacheTasks("h1", listOf(task(id = "t1"), task(id = "t2")))
        c.cacheAssignments("h1", "t1", listOf(assignment(id = "a1", taskId = "t1")))
        c.cacheAssignments("h1", "t2", listOf(assignment(id = "a2", taskId = "t2")))

        c.clearHousehold("h1")

        assertNull(c.getCachedAssignments("h1", "t1"))
        assertNull(c.getCachedAssignments("h1", "t2"))
    }

    /** Borrar la caché de un hogar no afecta a la caché de otros hogares. */
    @Test
    fun clearHousehold_doesNotAffectOtherHouseholds() {
        val c = cache()
        c.cacheTasks("h1", listOf(task(householdId = "h1")))
        c.cacheTasks("h2", listOf(task(id = "t2", householdId = "h2")))

        c.clearHousehold("h1")

        assertNull(c.getCachedTasks("h1"))
        assertEquals(listOf(task(id = "t2", householdId = "h2")), c.getCachedTasks("h2"))
    }
}

/** Doble de prueba mínimo de [Settings], en memoria — ver KDoc de [SettingsStoreTest]. */
private class FakeCacheSettings(initial: MutableMap<String, Any> = mutableMapOf()) : Settings {
    private val map = initial

    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size

    override fun clear() { map.clear() }
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)

    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int

    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long

    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String

    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float

    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double

    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}
