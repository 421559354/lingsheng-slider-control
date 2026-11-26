package com.lingsheng.slidercontrol.examples

data class ExampleEntry(
    val id: String,
    val title: String,
    val sourcePath: String,
    val category: String,
    val summary: String,
    val textAsset: String,
)

data class ProgramPresetGroup(
    val setupCode: Int,
    val label: String,
    val slotBase: Int,
    val slotCount: Int,
    val runCommandId: Int,
)

data class ProgramInstruction(
    val rowIndex: Int,
    val opcode: Int,
    val value: Int,
    val sourceCode: String,
    val note: String,
)

enum class RecipeConfirmation {
    InfiniteLoop,
    ExternalHardware,
    DirectContinuousMotion,
}

data class ProgramGroupRecipe(
    val preset: ProgramPresetGroup,
    val instructions: List<ProgramInstruction>,
)

data class ExampleProgramPackage(
    val groups: List<ProgramGroupRecipe>,
    val entrySetupCode: Int,
) {
    val entryGroup: ProgramGroupRecipe?
        get() = groups.firstOrNull { it.preset.setupCode == entrySetupCode }
}

data class ExampleParseResult(
    val recipe: ExampleProgramPackage?,
    val unavailableReason: String?,
    val notes: List<String> = emptyList(),
    val alternatives: List<ExampleProgramPackage> = emptyList(),
)

data class LoadedExample(
    val entry: ExampleEntry,
    val sourceText: String,
    val parseResult: ExampleParseResult,
)

data class ProgramGroupWritePlan(
    val preset: ProgramPresetGroup,
    val frames: List<ByteArray>,
)

data class ProgramWritePlan(
    val groups: List<ProgramGroupWritePlan>,
    val entryGroup: ProgramPresetGroup,
    val startFrame: ByteArray,
    val confirmations: Set<RecipeConfirmation>,
)

sealed interface ProgramPlanResult {
    data class Ready(val plan: ProgramWritePlan) : ProgramPlanResult
    data class Rejected(val reason: String) : ProgramPlanResult
}
