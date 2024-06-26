package felis.side

import felis.ModLoader
import felis.transformer.ClassContainer
import felis.transformer.Transformation

object SideStrippingTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        if (ModLoader.isAuditing) return

        val locator = StripLocator()
        container.walk(locator)
        if (locator.skipEntire) {
            container.skip = true
            return
        }

        if (locator.methods.size == 0 && locator.fields.size == 0) return
        container.visitor { ClassStripper(it, locator.methods, locator.fields) }
    }
}
