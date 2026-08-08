package com.badminton.bot.service;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import com.badminton.bot.domain.Event;
import com.badminton.bot.repo.BookingRepository;
import com.badminton.bot.repo.EventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Вся логика вместимости, записи, отмены и продвижения из листа ожидания.
 * Каждая мутирующая операция блокирует строку {@link Event} ({@code SELECT ... FOR UPDATE}),
 * что сериализует конкурентные записи на одно и то же событие и защищает от гонок при
 * одновременных нажатиях на последний свободный слот. Разные события не блокируют друг друга.
 */
@Slf4j
@Service
public class BookingService {

    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED);

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final SlotCalculator slotCalculator;
    private final BadmintonProperties properties;

    public BookingService(EventRepository eventRepository,
                           BookingRepository bookingRepository,
                           SlotCalculator slotCalculator,
                           BadmintonProperties properties) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.slotCalculator = slotCalculator;
        this.properties = properties;
    }

    public List<Booking> activeBookings(Long eventId) {
        return bookingRepository.findByEventIdAndStatusIn(eventId, ACTIVE_STATUSES);
    }

    public List<Booking> myActiveBookings(Long eventId, Long telegramUserId) {
        return bookingRepository.findByEventIdAndTelegramUserIdAndStatusIn(eventId, telegramUserId, ACTIVE_STATUSES);
    }

    @Transactional
    public BookingResult book(Long eventId, Long telegramUserId, String displayName, String username,
                               int startSlot, int durationMinutes, int partySize) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));

        if (!event.isOpen()) {
            return BookingResult.error(BookingOutcome.EVENT_NOT_OPEN);
        }
        if (partySize > properties.slotCapacity()) {
            return BookingResult.error(BookingOutcome.PARTY_TOO_BIG);
        }

        List<Booking> active = activeBookings(eventId);

        boolean overlapsOwn = active.stream()
                .filter(b -> b.getTelegramUserId().equals(telegramUserId))
                .anyMatch(b -> slotCalculator.overlaps(b.getStartSlot(), b.getDurationMinutes(), startSlot, durationMinutes));
        if (overlapsOwn) {
            return BookingResult.error(BookingOutcome.OVERLAPS_OWN_BOOKING);
        }

        int[] remaining = slotCalculator.remainingCapacityPerSlot(active);
        boolean fits = slotCalculator.fitsCapacity(remaining, startSlot, durationMinutes, partySize);

        Booking booking = Booking.builder()
                .event(event)
                .telegramUserId(telegramUserId)
                .displayName(displayName)
                .username(username)
                .startSlot(startSlot)
                .durationMinutes(durationMinutes)
                .partySize(partySize)
                .status(fits ? BookingStatus.CONFIRMED : BookingStatus.WAITLISTED)
                .createdAt(Instant.now())
                .build();
        booking = bookingRepository.save(booking);

        log.info("Booking {} event={} user={} slot={} duration={} size={} -> {}",
                booking.getId(), eventId, telegramUserId, startSlot, durationMinutes, partySize, booking.getStatus());

        return BookingResult.of(fits ? BookingOutcome.CONFIRMED : BookingOutcome.WAITLISTED, booking);
    }

    /**
     * Отменяет запись и пытается продвинуть из листа ожидания те записи, которым теперь хватает места.
     *
     * @return список записей, которые были продвинуты в CONFIRMED (нужно уведомить этих пользователей).
     */
    @Transactional
    public List<Booking> cancel(Long bookingId, Long requesterUserId, boolean requesterIsAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Запись не найдена: " + bookingId));

        if (!requesterIsAdmin && !booking.getTelegramUserId().equals(requesterUserId)) {
            throw new SecurityException("Нельзя отменить чужую запись");
        }
        if (!booking.isActive()) {
            return List.of();
        }

        Long eventId = booking.getEvent().getId();
        // Блокируем строку события, чтобы отмена и последующее продвижение из waitlist
        // были атомарны относительно параллельных попыток записи на то же событие.
        eventRepository.findByIdForUpdate(eventId);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return promoteWaitlist(eventId);
    }

    /** Пробегает лист ожидания в порядке очереди и подтверждает всех, кому теперь хватает места. */
    private List<Booking> promoteWaitlist(Long eventId) {
        List<Booking> active = activeBookings(eventId);
        List<Booking> waitlisted = active.stream()
                .filter(b -> b.getStatus() == BookingStatus.WAITLISTED)
                .sorted(Comparator.comparing(Booking::getCreatedAt))
                .toList();

        List<Booking> promoted = new java.util.ArrayList<>();
        List<Booking> confirmedSoFar = new java.util.ArrayList<>(
                active.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).toList());

        for (Booking candidate : waitlisted) {
            int[] remaining = slotCalculator.remainingCapacityPerSlot(confirmedSoFar);
            if (slotCalculator.fitsCapacity(remaining, candidate.getStartSlot(), candidate.getDurationMinutes(), candidate.getPartySize())) {
                candidate.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(candidate);
                confirmedSoFar.add(candidate);
                promoted.add(candidate);
            }
        }
        return promoted;
    }

    public Optional<Booking> findById(Long bookingId) {
        return bookingRepository.findById(bookingId);
    }
}
