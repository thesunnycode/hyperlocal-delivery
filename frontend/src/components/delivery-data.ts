export type ShipmentStatus = 'In transit' | 'Needs attention' | 'Delivered' | 'New' | 'Assigned' | 'Picked up' | 'Out for delivery' | 'Failed' | 'Returned' | 'Cancelled';
export type Shipment = { id: string; customer: string; destination: string; rider: string; status: ShipmentStatus; time: string; parcel: string };
export const shipments: Shipment[] = [
  { id: 'HL-1042', customer: 'Nandini Stores', destination: 'Indiranagar, Bengaluru', rider: 'Ravi Kumar', status: 'In transit', time: '14:20', parcel: '2 parcels' },
  { id: 'HL-1041', customer: 'Bright Pharmacy', destination: 'Koramangala, Bengaluru', rider: 'Asha Rao', status: 'Needs attention', time: '13:55', parcel: 'Medicine box' },
  { id: 'HL-1039', customer: 'Karuna Bakery', destination: 'Jayanagar, Bengaluru', rider: 'Priya Nair', status: 'Delivered', time: '13:10', parcel: '6 items' },
  { id: 'HL-1038', customer: 'City Mart', destination: 'MG Road, Bengaluru', rider: 'Unassigned', status: 'New', time: '12:48', parcel: '1 parcel' },
  { id: 'HL-1037', customer: 'Green Basket', destination: 'Whitefield, Bengaluru', rider: 'Ravi Kumar', status: 'Assigned', time: '12:15', parcel: '3 parcels' },
];
export const riders = [
  { name: 'Ravi Kumar', initials: 'RK', state: 'On the road', deliveries: 8 },
  { name: 'Asha Rao', initials: 'AR', state: 'On the road', deliveries: 6 },
  { name: 'Priya Nair', initials: 'PN', state: 'Available', deliveries: 5 },
  { name: 'Dev Shah', initials: 'DS', state: 'Off shift', deliveries: 4 },
];
