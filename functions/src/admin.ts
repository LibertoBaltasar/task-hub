/**
 * Inicialización única del Admin SDK — importado por cada archivo de función
 * (Cloud Functions reutiliza la instancia entre invocaciones "calientes" del
 * mismo contenedor; `initializeApp()` sin argumentos toma las credenciales
 * del entorno de ejecución de Cloud Functions automáticamente).
 */
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

if (getApps().length === 0) {
  initializeApp();
}

export const db = getFirestore();

export const REGION = "europe-west1";
