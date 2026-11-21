package eu.kanade.tachiyomi.source.model

class FilterList(list: List<Filter<*>>) : ArrayList<Filter<*>>(list) {
    constructor(vararg fs: Filter<*>) : this(fs.asList())
}
