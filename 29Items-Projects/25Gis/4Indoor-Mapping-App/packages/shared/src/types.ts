export interface VenueSummary {
  id: string;
  name: string;
  venueType: 'mall' | 'airport';
  timezone: string;
}

export interface FloorSummary {
  id: string;
  venueId: string;
  level: number;
  name: string;
}

export interface PositionEstimate {
  venueId: string;
  floorId: string;
  latitude: number;
  longitude: number;
  confidence: number;
  calculatedAt: string;
}

export interface PoiSummary {
  id: string;
  venueId: string;
  floorId: string;
  name: string;
  category: string;
  description: string | null;
  latitude: number;
  longitude: number;
}

export interface RouteDirections {
  venueId: string;
  fromNodeId: string;
  toNodeId: string;
  totalCost: number;
  nodeIds: string[];
}
