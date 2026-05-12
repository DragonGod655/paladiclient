/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.waypoints.events;

import paladi.client.systems.waypoints.Waypoint;

public record WaypointRemovedEvent(Waypoint waypoint) {
}
