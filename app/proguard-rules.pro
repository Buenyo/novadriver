# O NotificationListenerService é referenciado só pelo manifest; o AGP já gera a regra keep.
# Remove chamadas de log verbose/debug do release (menos trabalho no Cortex-A7).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
