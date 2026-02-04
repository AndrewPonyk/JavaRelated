import React, { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight, Plus, Clock, MapPin, X } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Label } from '@/components/ui/Label';
import { cn } from '@/utils/cn';
import { useToastHelpers } from '@/components/ui/Toaster';
import {
  getEvents,
  createEvent,
  updateEvent,
  deleteEvent,
  CalendarEvent,
  CreateEventInput,
} from '@/services/api/calendarApi';

const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

const eventTypes = [
  { value: 'MEETING', label: 'Meeting', color: 'blue' },
  { value: 'REVIEW', label: 'Review', color: 'green' },
  { value: 'PRESENTATION', label: 'Presentation', color: 'purple' },
  { value: 'PLANNING', label: 'Planning', color: 'orange' },
  { value: 'DEADLINE', label: 'Deadline', color: 'red' },
  { value: 'REMINDER', label: 'Reminder', color: 'yellow' },
  { value: 'OTHER', label: 'Other', color: 'gray' },
] as const;

const getColorStyle = (color: string) => {
  const colors: Record<string, { bg: string; text: string }> = {
    blue: { bg: '#dbeafe', text: '#1d4ed8' },
    green: { bg: '#dcfce7', text: '#15803d' },
    purple: { bg: '#f3e8ff', text: '#7e22ce' },
    orange: { bg: '#ffedd5', text: '#c2410c' },
    red: { bg: '#fee2e2', text: '#b91c1c' },
    yellow: { bg: '#fef3c7', text: '#92400e' },
    gray: { bg: '#f3f4f6', text: '#374151' },
  };
  return colors[color] || colors.gray;
};

export const Calendar: React.FC = () => {
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<Date | null>(null);
  const [view, setView] = useState<'month' | 'week' | 'day'>('month');
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showEventDialog, setShowEventDialog] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CalendarEvent | null>(null);
  const { success: showSuccess, error: showError } = useToastHelpers();

  // Event form state
  const [eventForm, setEventForm] = useState<CreateEventInput>({
    title: '',
    description: '',
    startTime: '',
    endTime: '',
    type: 'MEETING',
    color: 'blue',
    location: '',
    allDay: false,
  });

  useEffect(() => {
    loadEvents();
  }, [currentDate]);

  const loadEvents = async () => {
    setIsLoading(true);
    try {
      const year = currentDate.getFullYear();
      const month = currentDate.getMonth();
      const start = new Date(year, month, 1).toISOString();
      const end = new Date(year, month + 1, 0, 23, 59, 59).toISOString();
      const result = await getEvents({ start, end });
      setEvents(result.data);
    } catch (err) {
      showError('Failed to load events', 'Could not load calendar events');
    } finally {
      setIsLoading(false);
    }
  };

  const getDaysInMonth = (date: Date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const daysInMonth = lastDay.getDate();
    const startingDay = firstDay.getDay();

    const days: (number | null)[] = [];
    for (let i = 0; i < startingDay; i++) days.push(null);
    for (let i = 1; i <= daysInMonth; i++) days.push(i);
    return days;
  };

  const days = getDaysInMonth(currentDate);

  const navigateMonth = (delta: number) => {
    setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + delta, 1));
  };

  const getEventsForDay = (day: number) => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    return events.filter(e => {
      const eventDate = new Date(e.startTime);
      return eventDate.getFullYear() === year &&
             eventDate.getMonth() === month &&
             eventDate.getDate() === day;
    });
  };

  const selectedDateEvents = selectedDate ? getEventsForDay(selectedDate.getDate()) : [];

  const openAddEvent = (date?: Date) => {
    const d = date || selectedDate || new Date();
    const dateStr = d.toISOString().split('T')[0];
    setEventForm({
      title: '',
      description: '',
      startTime: `${dateStr}T09:00`,
      endTime: `${dateStr}T10:00`,
      type: 'MEETING',
      color: 'blue',
      location: '',
      allDay: false,
    });
    setEditingEvent(null);
    setShowEventDialog(true);
  };

  const openEditEvent = (event: CalendarEvent) => {
    setEventForm({
      title: event.title,
      description: event.description || '',
      startTime: event.startTime.slice(0, 16),
      endTime: event.endTime?.slice(0, 16) || '',
      type: event.type,
      color: event.color,
      location: event.location || '',
      allDay: event.allDay,
    });
    setEditingEvent(event);
    setShowEventDialog(true);
  };

  const handleSaveEvent = async () => {
    if (!eventForm.title.trim()) {
      showError('Validation Error', 'Title is required');
      return;
    }

    try {
      const data: CreateEventInput = {
        ...eventForm,
        startTime: new Date(eventForm.startTime!).toISOString(),
        endTime: eventForm.endTime ? new Date(eventForm.endTime).toISOString() : undefined,
      };

      if (editingEvent) {
        const updated = await updateEvent(editingEvent.id, data);
        setEvents(events.map(e => e.id === editingEvent.id ? updated.data : e));
        showSuccess('Event Updated', 'Your event has been updated');
      } else {
        const created = await createEvent(data);
        setEvents([...events, created.data]);
        showSuccess('Event Created', 'Your event has been added to the calendar');
      }
      setShowEventDialog(false);
    } catch (err) {
      showError('Error', 'Failed to save event');
    }
  };

  const handleDeleteEvent = async (id: string) => {
    if (!confirm('Are you sure you want to delete this event?')) return;
    try {
      await deleteEvent(id);
      setEvents(events.filter(e => e.id !== id));
      showSuccess('Deleted', 'Event has been deleted');
    } catch (err) {
      showError('Error', 'Failed to delete event');
    }
  };

  const today = new Date();
  const isToday = (day: number) =>
    day === today.getDate() &&
    currentDate.getMonth() === today.getMonth() &&
    currentDate.getFullYear() === today.getFullYear();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Calendar</h1>
          <p className="text-gray-600">Schedule and manage your events</p>
        </div>
        <Button onClick={() => openAddEvent()}>
          <Plus className="mr-2 h-4 w-4" />
          Add Event
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Calendar Grid */}
        <Card className="p-6 lg:col-span-2">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold">
              {months[currentDate.getMonth()]} {currentDate.getFullYear()}
            </h2>
            <div className="flex items-center gap-2">
              <div className="flex gap-1">
                {(['month', 'week', 'day'] as const).map((v) => (
                  <button
                    key={v}
                    onClick={() => setView(v)}
                    className={cn(
                      'px-3 py-1 rounded text-sm capitalize',
                      view === v ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'
                    )}
                  >
                    {v}
                  </button>
                ))}
              </div>
              <div className="flex gap-1 ml-4">
                <Button variant="outline" size="icon" onClick={() => navigateMonth(-1)}>
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button variant="outline" size="icon" onClick={() => navigateMonth(1)}>
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-7 gap-1">
            {daysOfWeek.map((day) => (
              <div key={day} className="text-center text-sm font-medium text-gray-500 py-2">
                {day}
              </div>
            ))}
            {days.map((day, i) => {
              const dayEvents = day ? getEventsForDay(day) : [];
              const isTodayDay = day ? isToday(day) : false;
              const isSelected = selectedDate?.getDate() === day &&
                                 selectedDate?.getMonth() === currentDate.getMonth();

              return (
                <div
                  key={i}
                  className={cn(
                    'min-h-[80px] p-1 border rounded-lg cursor-pointer transition-colors',
                    day ? 'hover:bg-gray-50' : 'bg-gray-50',
                    isTodayDay && 'bg-blue-50 border-blue-200',
                    isSelected && 'ring-2 ring-blue-500'
                  )}
                  onClick={() => day && setSelectedDate(new Date(currentDate.getFullYear(), currentDate.getMonth(), day))}
                >
                  {day && (
                    <>
                      <span className={cn(
                        'text-sm font-medium',
                        isTodayDay ? 'text-blue-600' : 'text-gray-900'
                      )}>
                        {day}
                      </span>
                      <div className="mt-1 space-y-1">
                        {dayEvents.slice(0, 2).map((event) => {
                          const colorStyle = getColorStyle(event.color);
                          return (
                            <div
                              key={event.id}
                              className="text-xs px-1 py-0.5 rounded truncate"
                              style={{ backgroundColor: colorStyle.bg, color: colorStyle.text }}
                              onClick={(e) => { e.stopPropagation(); openEditEvent(event); }}
                            >
                              {event.title}
                            </div>
                          );
                        })}
                        {dayEvents.length > 2 && (
                          <div className="text-xs text-gray-500">+{dayEvents.length - 2} more</div>
                        )}
                      </div>
                    </>
                  )}
                </div>
              );
            })}
          </div>
        </Card>

        {/* Selected Day Events */}
        <Card className="p-6">
          <h3 className="font-semibold text-gray-900 mb-4">
            {selectedDate ? `Events for ${months[selectedDate.getMonth()]} ${selectedDate.getDate()}` : 'Select a day'}
          </h3>

          {selectedDateEvents.length > 0 ? (
            <div className="space-y-3">
              {selectedDateEvents.map((event) => (
                <div key={event.id} className="p-3 bg-gray-50 rounded-lg">
                  <div className="flex justify-between items-start">
                    <h4 className="font-medium text-gray-900">{event.title}</h4>
                    <button
                      onClick={() => handleDeleteEvent(event.id)}
                      className="text-gray-400 hover:text-red-500"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </div>
                  {event.description && (
                    <p className="text-sm text-gray-600 mt-1">{event.description}</p>
                  )}
                  <div className="mt-2 space-y-1 text-sm text-gray-600">
                    <div className="flex items-center">
                      <Clock className="h-4 w-4 mr-2" />
                      {new Date(event.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      {event.endTime && ` - ${new Date(event.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`}
                    </div>
                    {event.location && (
                      <div className="flex items-center">
                        <MapPin className="h-4 w-4 mr-2" />
                        {event.location}
                      </div>
                    )}
                  </div>
                  <Badge className="mt-2" variant="secondary">{event.type.toLowerCase()}</Badge>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="mt-2 w-full"
                    onClick={() => openEditEvent(event)}
                  >
                    Edit
                  </Button>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-gray-500 text-center py-8">
              {selectedDate ? 'No events scheduled' : 'Click a day to see events'}
            </p>
          )}

          {selectedDate && (
            <Button className="w-full mt-4" variant="outline" onClick={() => openAddEvent(selectedDate)}>
              <Plus className="mr-2 h-4 w-4" />
              Add Event
            </Button>
          )}
        </Card>
      </div>

      {/* Add/Edit Event Dialog */}
      <Dialog open={showEventDialog} onOpenChange={setShowEventDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingEvent ? 'Edit Event' : 'Add Event'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div>
              <Label htmlFor="title">Title</Label>
              <Input
                id="title"
                value={eventForm.title}
                onChange={(e) => setEventForm({ ...eventForm, title: e.target.value })}
                placeholder="Event title"
              />
            </div>
            <div>
              <Label htmlFor="description">Description</Label>
              <Input
                id="description"
                value={eventForm.description}
                onChange={(e) => setEventForm({ ...eventForm, description: e.target.value })}
                placeholder="Optional description"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="startTime">Start</Label>
                <Input
                  id="startTime"
                  type="datetime-local"
                  value={eventForm.startTime}
                  onChange={(e) => setEventForm({ ...eventForm, startTime: e.target.value })}
                />
              </div>
              <div>
                <Label htmlFor="endTime">End</Label>
                <Input
                  id="endTime"
                  type="datetime-local"
                  value={eventForm.endTime}
                  onChange={(e) => setEventForm({ ...eventForm, endTime: e.target.value })}
                />
              </div>
            </div>
            <div>
              <Label htmlFor="type">Type</Label>
              <select
                id="type"
                className="w-full rounded-md border border-gray-300 px-3 py-2"
                value={eventForm.type}
                onChange={(e) => {
                  const type = e.target.value as CalendarEvent['type'];
                  const color = eventTypes.find(t => t.value === type)?.color || 'blue';
                  setEventForm({ ...eventForm, type, color });
                }}
              >
                {eventTypes.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="location">Location</Label>
              <Input
                id="location"
                value={eventForm.location}
                onChange={(e) => setEventForm({ ...eventForm, location: e.target.value })}
                placeholder="Optional location"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setShowEventDialog(false)}>Cancel</Button>
            <Button onClick={handleSaveEvent}>{editingEvent ? 'Update' : 'Create'}</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default Calendar;
