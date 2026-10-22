package me.vladcip.guessthecountry.data.seed

import me.vladcip.guessthecountry.domain.model.Country

/**
 * Questions shipped with the app. Hints go from hardest (5 points) to easiest (2 points);
 * the capital is the final 1-point clue, so hints never mention it.
 */
object SeedCountries {
    val all: List<Country> = listOf(
        Country(
            id = "JP",
            name = "Japan",
            capital = "Tokyo",
            hints = listOf(
                "Its national parliament is called the Diet.",
                "It has the oldest continuous hereditary monarchy in the world.",
                "It is an archipelago of more than 14,000 islands, the largest being Honshu.",
                "Mount Fuji, sushi and the Shinkansen bullet train all come from here.",
            ),
        ),
        Country(
            id = "BR",
            name = "Brazil",
            capital = "Brasília",
            hints = listOf(
                "Its capital was purpose-built in the interior and inaugurated in 1960.",
                "It borders every South American country except Chile and Ecuador.",
                "It is the largest Portuguese-speaking country in the world.",
                "Rio de Janeiro's Carnival and five FIFA World Cup titles.",
            ),
        ),
        Country(
            id = "CA",
            name = "Canada",
            capital = "Ottawa",
            hints = listOf(
                "It has the longest coastline of any country in the world.",
                "It shares the world's longest international land border with a single neighbour.",
                "It is officially bilingual in English and French.",
                "A red maple leaf sits in the middle of its flag.",
            ),
        ),
        Country(
            id = "AU",
            name = "Australia",
            capital = "Canberra",
            hints = listOf(
                "Its capital was chosen as a compromise between its two largest cities.",
                "The world's largest coral reef system lies off its north-east coast.",
                "It is the only country that covers an entire continent.",
                "Kangaroos, koalas and the Sydney Opera House.",
            ),
        ),
        Country(
            id = "EG",
            name = "Egypt",
            capital = "Cairo",
            hints = listOf(
                "It spans two continents, with the Sinai Peninsula in Asia.",
                "The Suez Canal runs through its territory.",
                "Almost all of its people live along the valley and delta of one great river.",
                "The Great Pyramid of Giza and the Sphinx.",
            ),
        ),
        Country(
            id = "FR",
            name = "France",
            capital = "Paris",
            hints = listOf(
                "Thanks to its overseas territories, it spans more time zones than any other country.",
                "Its national motto is \"Liberty, Equality, Fraternity\".",
                "It is the most visited country in the world by international tourists.",
                "The Eiffel Tower and the Louvre.",
            ),
        ),
        Country(
            id = "IT",
            name = "Italy",
            capital = "Rome",
            hints = listOf(
                "Two independent microstates lie entirely within its borders.",
                "It has one of the highest numbers of UNESCO World Heritage Sites.",
                "Its mainland is shaped like a boot.",
                "Pizza, pasta and the Colosseum.",
            ),
        ),
        Country(
            id = "IN",
            name = "India",
            capital = "New Delhi",
            hints = listOf(
                "It has the longest written national constitution in the world.",
                "Its flag has a 24-spoke wheel in the centre.",
                "It is the most populous country in the world.",
                "The Taj Mahal and Bollywood.",
            ),
        ),
        Country(
            id = "MX",
            name = "Mexico",
            capital = "Mexico City",
            hints = listOf(
                "It has the largest Spanish-speaking population in the world.",
                "The Aztec city of Tenochtitlan once stood where its capital is today.",
                "Its flag shows an eagle on a cactus devouring a snake.",
                "Tacos, mariachi and the Day of the Dead.",
            ),
        ),
        Country(
            id = "KE",
            name = "Kenya",
            capital = "Nairobi",
            hints = listOf(
                "The equator crosses it, and it is home to Africa's second-highest mountain.",
                "Its flag has a Maasai shield and two crossed spears.",
                "Its long-distance runners have set many marathon world records.",
                "Safaris in the Maasai Mara, home of the Great Migration.",
            ),
        ),
        Country(
            id = "AR",
            name = "Argentina",
            capital = "Buenos Aires",
            hints = listOf(
                "Aconcagua, the highest peak outside Asia, is here.",
                "It claims the Falkland Islands, which it calls Las Malvinas.",
                "It is the birthplace of the tango.",
                "Diego Maradona and Lionel Messi led its national football team.",
            ),
        ),
        Country(
            id = "NO",
            name = "Norway",
            capital = "Oslo",
            hints = listOf(
                "It governs the Arctic archipelago of Svalbard.",
                "Its oil-funded sovereign wealth fund is the largest in the world.",
                "The Nobel Peace Prize is awarded here, not in Sweden.",
                "Fjords, Vikings and the midnight sun.",
            ),
        ),
        Country(
            id = "PE",
            name = "Peru",
            capital = "Lima",
            hints = listOf(
                "It shares Lake Titicaca with its neighbour Bolivia.",
                "The most distant source of the Amazon River is in its Andes mountains.",
                "The Nazca Lines are etched into its southern desert.",
                "Machu Picchu, the Inca citadel in the clouds.",
            ),
        ),
        Country(
            id = "ZA",
            name = "South Africa",
            capital = "Pretoria",
            hints = listOf(
                "It has three capital cities, one for each branch of government.",
                "It has 12 official languages.",
                "It completely surrounds another country, Lesotho.",
                "Nelson Mandela was its first democratically elected president.",
            ),
        ),
        Country(
            id = "TH",
            name = "Thailand",
            capital = "Bangkok",
            hints = listOf(
                "Its official calendar counts years from the Buddha's death, 543 years ahead of ours.",
                "It is the only Southeast Asian country never colonised by a European power.",
                "Phuket and Koh Samui are among its best-known islands.",
                "It was known as Siam until 1939.",
            ),
        ),
        Country(
            id = "MA",
            name = "Morocco",
            capital = "Rabat",
            hints = listOf(
                "It is the only African country with coastlines on both the Atlantic and the Mediterranean.",
                "Only the 14 km Strait of Gibraltar separates it from Spain.",
                "Its largest city is Casablanca.",
                "The souks of Marrakesh and the Atlas Mountains.",
            ),
        ),
        Country(
            id = "GR",
            name = "Greece",
            capital = "Athens",
            hints = listOf(
                "The nine stripes on its flag are said to stand for the syllables of its motto.",
                "It has around 6,000 islands and islets.",
                "It is seen as the birthplace of democracy and the Olympic Games.",
                "Santorini's white houses and blue domes.",
            ),
        ),
        Country(
            id = "NZ",
            name = "New Zealand",
            capital = "Wellington",
            hints = listOf(
                "In 1893 it became the first self-governing country to give women the vote.",
                "Its Māori name is Aotearoa.",
                "Its rugby team, the All Blacks, performs the haka before matches.",
                "Kiwi birds and the filming locations of The Lord of the Rings.",
            ),
        ),
        Country(
            id = "VN",
            name = "Vietnam",
            capital = "Hanoi",
            hints = listOf(
                "It is the world's second-largest coffee exporter.",
                "Its long, narrow territory forms an S shape along the South China Sea.",
                "Ha Long Bay's limestone islands rise from its northern waters.",
                "Pho noodle soup and the Mekong Delta.",
            ),
        ),
        Country(
            id = "IS",
            name = "Iceland",
            capital = "Reykjavík",
            hints = listOf(
                "Its parliament, the Althing, was founded in 930.",
                "It has no standing army.",
                "It sits on the Mid-Atlantic Ridge, full of volcanoes and geysers.",
                "The Blue Lagoon and the northern lights over a land of fire and ice.",
            ),
        ),
        Country(
            id = "DE",
            name = "Germany",
            capital = "Berlin",
            hints = listOf(
                "It shares land borders with nine countries.",
                "It was reunified in 1990 after decades of division.",
                "Parts of its autobahn network have no general speed limit.",
                "Oktoberfest, bratwurst and pretzels.",
            ),
        ),
    )
}
