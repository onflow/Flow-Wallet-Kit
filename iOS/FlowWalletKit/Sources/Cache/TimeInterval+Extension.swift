import Foundation

/// Extension to make TimeInterval more readable for cache durations
public extension TimeInterval {
    /// Convert minutes to TimeInterval
    static func minutes(_ value: Double) -> TimeInterval {
        value * 60
    }
    
    /// Convert hours to TimeInterval
    static func hours(_ value: Double) -> TimeInterval {
        value * 60 * 60
    }
    
    /// Convert days to TimeInterval
    static func days(_ value: Double) -> TimeInterval {
        value * 24 * 60 * 60
    }
    
    /// Convert weeks to TimeInterval
    static func weeks(_ value: Double) -> TimeInterval {
        value * 7 * 24 * 60 * 60
    }
    
    /// Convert months to TimeInterval (assuming 30 days per month)
    static func months(_ value: Double) -> TimeInterval {
        value * 30 * 24 * 60 * 60
    }
    
    /// Convert years to TimeInterval (assuming 365 days per year)
    static func years(_ value: Double) -> TimeInterval {
        value * 365 * 24 * 60 * 60
    }
    
    /// Common cache durations
    static let minute: TimeInterval = minutes(1)
    static let hour: TimeInterval = hours(1)
    static let day: TimeInterval = days(1)
    static let week: TimeInterval = weeks(1)
    static let month: TimeInterval = months(1)
    static let year: TimeInterval = years(1)
}